package com.chtrembl.petstore.order.api;

import com.chtrembl.petstore.order.model.Order;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ItemReserveServiceImpl implements ItemReserveService{

    private static final Logger logger = LoggerFactory.getLogger(ItemReserveServiceImpl.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${petstore.order.save.url:}")
    private String itemReserveServiceImpl;

    @Override
    public void saveOrderDataInBlob(Order order) throws JsonProcessingException {
        String orderJSON = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false).writeValueAsString(order);
        logger.info("order json before sending to function {}", orderJSON);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Accept", MediaType.APPLICATION_JSON_VALUE);
            headers.add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Order> entity = new HttpEntity<>(order, headers);
            restTemplate.exchange(String.format("%s/api/processOrder", this.itemReserveServiceImpl), HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            logger.error(String.format(
                    "Error while saving order details to blob %s", e.getMessage()));
        }
    }
}
