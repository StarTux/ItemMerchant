package com.cavetale.itemmerchant;

import java.io.File;
import java.util.stream.Collectors;
import org.junit.Test;

public final class ItemMerchantTest {
    public ItemMerchantTest() { }

    @Test
    public void test() {
        String str = ItemMerchantPlugin.importMaterialPrices(new File("src/main/resources/prices.yml"))
            .entrySet()
            .stream().map(e -> "" + e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
        System.out.println(str);
    }
}
