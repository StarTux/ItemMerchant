package com.cavetale.itemmerchant;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

@Table(name = "items",
       uniqueConstraints = {@UniqueConstraint(columnNames = "material")})
@Getter @Setter
public final class SQLItem {
    @Id private Integer id;
    @Column(length = 32, nullable = false) private String material;
    // Settings
    @Column(nullable = false) private Double basePrice;
    @Column(nullable = false) private Double timeOffset;
    @Column(nullable = false) private Integer capacity;
    // State
    @Column(nullable = false) private Integer storage;
    @Column(nullable = false) private Double price;
    @Version private Date version;

    SQLItem() { }

    SQLItem(Material mat, double bp, int cap) {
        this.material = mat.name().toLowerCase();
        this.basePrice = bp;
        this.timeOffset = Math.random();
        this.capacity = cap;
        this.storage = cap;
        this.price = bp;
    }
}
