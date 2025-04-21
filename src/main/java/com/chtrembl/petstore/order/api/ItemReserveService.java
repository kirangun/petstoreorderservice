package com.chtrembl.petstore.order.api;

import com.chtrembl.petstore.order.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;

public interface ItemReserveService {
    void saveOrderDataInBlob(Order order) throws JsonProcessingException;
}
