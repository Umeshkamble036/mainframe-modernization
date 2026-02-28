package com.custacct.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter to map CustomerStatus enum to single-character database column.
 * Converts enum (ACTIVE, INACTIVE, etc.) to/from single-char codes (A, I, etc.)
 */
@Converter(autoApply = true)
public class CustomerStatusConverter implements AttributeConverter<Customer.CustomerStatus, String> {

    @Override
    public String convertToDatabaseColumn(Customer.CustomerStatus enumValue) {
        return enumValue == null ? null : enumValue.getCode();
    }

    @Override
    public Customer.CustomerStatus convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : Customer.CustomerStatus.fromCode(dbValue);
    }
}
