package com.cavetale.itemmerchant;

import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;
import org.bukkit.Material;

@Data
@Table(name = "prices",
       uniqueConstraints = @UniqueConstraint(name = "material",
                                             columnNames = {"material"}))
public final class SQLPrice {
    @Id Integer id;
    @Column(nullable = false, length = 64) String material;
    @Column(nullable = false) Double price;

    public SQLPrice() { }

    SQLPrice(final String material, final double price) {
        this.material = Objects.requireNonNull(material);
        this.price = price;
    }

    SQLPrice(final Material material, final double price) {
        this(material.name().toLowerCase(), price);
    }
}
