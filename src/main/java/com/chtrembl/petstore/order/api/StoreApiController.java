package com.chtrembl.petstore.order.api;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.chtrembl.petstore.order.model.ContainerEnvironment;
import com.chtrembl.petstore.order.model.Order;
import com.chtrembl.petstore.order.model.Product;
import com.chtrembl.petstore.order.repository.OrderRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.NativeWebRequest;

import javax.annotation.Generated;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2021-12-21T10:17:19.885-05:00")

@Controller
@RequestMapping("${openapi.swaggerPetstore.base-path:/petstoreorderservice/v2}")
public class StoreApiController implements StoreApi {

	static final Logger log = LoggerFactory.getLogger(StoreApiController.class);

	private final ObjectMapper objectMapper;

	private final NativeWebRequest request;

	@Autowired
	@Qualifier(value = "cacheManager")
	private CacheManager cacheManager;

	@Autowired
	private ContainerEnvironment containerEnvironment;

	@Autowired
	private StoreApiCache storeApiCache;

	@Autowired
	private OrderRepository orderRepository;

	@Value("${petstore.servicebus.connectionString:}")
	private String serviceBusConnectionString;

	@Value("${petstore.servicebus.queueName:}")
	private String queueName;

	@Override
	public StoreApiCache getBeanToBeAutowired() {
		return storeApiCache;
	}

	@Autowired
	public StoreApiController(ObjectMapper objectMapper, NativeWebRequest request) {
		this.objectMapper = objectMapper;
		this.request = request;
	}

	// should really be in an interceptor
	public void conigureThreadForLogging() {
		try {
			this.containerEnvironment.setContainerHostName(
					InetAddress.getLocalHost().getHostAddress() + "/" + InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			log.info("PetStoreOrderService getRequest() error: " + e.getMessage());
		}
		MDC.put("containerHostName", this.containerEnvironment.getContainerHostName());
		MDC.put("session_Id", request.getHeader("session-id"));
	}

	@RequestMapping(value = "store/info", produces = { "application/json" }, method = RequestMethod.GET)
	public ResponseEntity<String> info() {
		conigureThreadForLogging();

		// password used for cred scan demo
		String password = "foobar";
		log.info("PetStoreOrderService incoming GET request to petstoreorderservice/v2/info");

		int ordersCacheSize = 0;
		try {
			ConcurrentMapCache mapCache = ((ConcurrentMapCache) this.cacheManager
					.getCache("orders"));
			ordersCacheSize = mapCache.getNativeCache().size();
		} catch (Exception e) {
			log.warn(String.format("could not get the orders cache size :%s", e.getMessage()));
		}

		// giving consumers JSON regardless here, info wasn't part of the swagger
		// contract :)
		ApiUtil.setResponse(request, "application/json",
				"{ \"service\" : \"order service\", \"version\" : \"" + containerEnvironment.getAppVersion()
						+ "\", \"container\" : \"" + containerEnvironment.getContainerHostName()
						+ "\", \"ordersCacheSize\" : \"" + ordersCacheSize + "\", \"author\" : \"" + containerEnvironment.getAuthor()
						+ "\" }");
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@Override
	public ResponseEntity<Order> placeOrder(
			@ApiParam(value = "order placed for purchasing the product", required = true) @Valid @RequestBody Order body) {
		conigureThreadForLogging();

		String acceptType = request.getHeader("Content-Type");
		String contentType = request.getHeader("Content-Type");
		if (acceptType != null && contentType != null && acceptType.contains("application/json")
				&& contentType.contains("application/json")) {

			log.info(String.format(
					"PetStoreOrderService incoming POST request to petstoreorderservice/v2/order/placeOder for order id:%s",
					body.getId()));

			Order order = null;
			// fetch from db.
			Order existingOrder = orderRepository.findById(body.getId()).orElse(null);
			List<Product> existingProducts = new ArrayList<>();

			// 1 product is just an add from a product page so cache needs to be updated
			if (body.getProducts() != null && body.getProducts().size() == 1) {
				Product incomingProduct = body.getProducts().get(0);
				if (existingOrder != null && existingOrder.getProducts() != null) {
					existingProducts = existingOrder.getProducts();
				}
				if (existingProducts != null && existingProducts.size() > 0) {
					// removal if one exists...
					if (incomingProduct.getQuantity() == 0) {
						existingProducts.removeIf(product -> product.getId().equals(incomingProduct.getId()));
						existingOrder.setProducts(existingProducts);
					}
					// update quantity if one exists or add new entry
					else {

						Product product = existingProducts.stream()
								.filter(existingProduct -> existingProduct.getId().equals(incomingProduct.getId()))
								.findAny().orElse(null);
						if (product != null) {
							// one exists
							int qty = product.getQuantity() + incomingProduct.getQuantity();
							// if the count falls below 1, remove it
							if (qty < 1) {
								existingProducts.removeIf(p -> p.getId().equals(incomingProduct.getId()));
							} else if (qty < 11) {
								product.setQuantity(qty);
							}
						} else {
							// existing products but one does not exist matching the incoming product
							existingOrder.addProductsItem(body.getProducts().get(0));
						}
					}
				} else {
					// nothing existing....
					if (body.getProducts().get(0).getQuantity() > 0) {
						order = new Order();
						order.setComplete(body.isComplete());
						order.setId(body.getId());
						order.setEmail(body.getEmail());
						order.setProducts(body.getProducts());
					}
				}
				if (existingOrder != null) {
					order = existingOrder;
				}
			}
			// n products is the current order being modified and so cache can be replaced
			// with it
			if (body.getProducts() != null && body.getProducts().size() > 1) {
				existingOrder.setProducts(body.getProducts());
				order = existingOrder;
			}

			try {
				//saving order details into DB
				Order saveIntoDB = orderRepository.save(order);
				String orderJSON = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
						.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
						.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false).writeValueAsString(saveIntoDB);

				//sending order detail to queue
				ServiceBusSenderClient client = new ServiceBusClientBuilder()
						.connectionString(serviceBusConnectionString).sender().queueName(queueName).buildClient();

				client.sendMessage(new ServiceBusMessage(orderJSON));
				log.info("Order Details sent to queue {} ", queueName);

				ApiUtil.setResponse(request, "application/json", orderJSON);
				return new ResponseEntity<>(HttpStatus.OK);
			} catch (IOException e) {
				log.error("Couldn't serialize response for content type application/json", e);
				return new ResponseEntity<Order>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		return new ResponseEntity<Order>(HttpStatus.NOT_IMPLEMENTED);

	}

	@Override
	public ResponseEntity<Order> getOrderById(
			@ApiParam(value = "ID of the order that needs to be deleted", required = true) @PathVariable("orderId") String orderId) {
		conigureThreadForLogging();

		String acceptType = request.getHeader("Content-Type");
		String contentType = request.getHeader("Content-Type");
		if (acceptType != null && contentType != null && acceptType.contains("application/json")
				&& contentType.contains("application/json")) {

			log.info(String.format(
					"PetStoreOrderService incoming GET request to petstoreorderservice/v2/order/getOrderById for order id:%s",
					orderId));

			List<Product> products = this.storeApiCache.getProducts();

			Order order = orderRepository.findById(orderId).orElse(null);

			if (products != null) {
				// cross reference order data (order only has product id and qty) with product
				// data....
				try {
					if (order.getProducts() != null) {
						for (Product p : order.getProducts()) {
							Product peekedProduct = getProduct(products, p.getId());
							p.setName(peekedProduct.getName());
							p.setPhotoURL((peekedProduct.getPhotoURL()));
						}
					}
				} catch (Exception e) {
					log.error(String.format(
							"PetStoreOrderService incoming GET request to petstoreorderservice/v2/order/getOrderById for order id:%s failed: %s",
							orderId, e.getMessage()));
				}
			}

			try {
				ApiUtil.setResponse(request, "application/json", new ObjectMapper().writeValueAsString(order));
				return new ResponseEntity<>(HttpStatus.OK);
			} catch (IOException e) {
				log.error("Couldn't serialize response for content type application/json", e);
				return new ResponseEntity<Order>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		return new ResponseEntity<Order>(HttpStatus.NOT_IMPLEMENTED);
	}

	private Product getProduct(List<Product> products, Long id) {
		conigureThreadForLogging();

		return products.stream().filter(product -> id.equals(product.getId())).findAny().orElse(null);
	}

	@Override
	public ResponseEntity<Void> deleteOrder(
			@Min(1L) @ApiParam(value = "ID of the order that needs to be deleted", required = true) @PathVariable("orderId") String orderId) {
		return new ResponseEntity<Void>(HttpStatus.NOT_IMPLEMENTED);
	}

	@Override
	public ResponseEntity<Map<String, Integer>> getInventory() {
		String accept = request.getHeader("Accept");
		if (accept != null && accept.contains("application/json")) {
			try {
				return new ResponseEntity<Map<String, Integer>>(objectMapper.readValue("{  \"key\" : 0}", Map.class),
						HttpStatus.NOT_IMPLEMENTED);
			} catch (IOException e) {
				log.error("Couldn't serialize response for content type application/json", e);
				return new ResponseEntity<Map<String, Integer>>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		return new ResponseEntity<Map<String, Integer>>(HttpStatus.NOT_IMPLEMENTED);
	}
}
