package com.cavetale.itemmerchant;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.Material;

@Data
@Table(name = "logs",
       indexes = {@Index(columnList = "player"),
                  @Index(columnList = "material"),
                  @Index(columnList = "time")})
public final class SQLLog {
    @Id Integer id;
    @Column(nullable = false) UUID player;
    @Column(nullable = false, length = 64) String material;
    @Column(nullable = false) Integer amount;
    @Column(nullable = false) Double price;
    @Column(nullable = false) Date time;

    public SQLLog() { }

    SQLLog(final UUID player, final String material, final int amount, final double price) {
        this.player = Objects.requireNonNull(player);
        this.material = Objects.requireNonNull(material);
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        this.amount = amount;
        if (price < 0.01) throw new IllegalArgumentException("price must be significant");
        this.price = price;
        this.time = new Date();
    }

    SQLLog(final UUID player, final Material material, final int amount, final double price) {
        this(player, material.name().toLowerCase(), amount, price);
    }
}
