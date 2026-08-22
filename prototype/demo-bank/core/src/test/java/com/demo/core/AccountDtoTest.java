package com.demo.core;

import com.tinytest.Test;
import static com.tinytest.Assert.*;

public class AccountDtoTest {

    // SMELL: pure getter/setter coverage inflation, no business behaviour
    @Test
    public void testGettersAndSetters() {
        AccountDto dto = new AccountDto();
        dto.setId("A1");
        dto.setOwner("Ada");
        dto.setBalance(100.0);
        assertEquals("A1", dto.getId());
        assertEquals("Ada", dto.getOwner());
        assertEquals(100.0, dto.getBalance(), 0.001);
    }
}
