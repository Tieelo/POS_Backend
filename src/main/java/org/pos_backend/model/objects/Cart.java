package org.pos_backend.model.objects;

import org.pos_backend.model.database.Inventory;
import org.pos_backend.model.database.Invoice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Cart {
    private Inventory inventory;
    private Invoice invoice;
    private static Cart singleInstance = null;
    private Map<Item, Integer> itemsInCart;
    private Map<Integer, Item> itemIdMap;
    private Cart() {
        inventory = Inventory.getInstance();
        invoice = new Invoice();
        itemsInCart = new HashMap<>();
        itemIdMap = new HashMap<>();
    }
    public Item getItemById(Integer id) {
        return itemIdMap.get(id);
    }
    public static Cart getInstance() {
        if (singleInstance == null) {
            synchronized (Cart.class) {
                if (singleInstance == null) {
                    singleInstance = new Cart();
                }
            }
        }
        return singleInstance;
    }
    private void addItem(Item item, int amount) {
        if (item == null){
            return;
        }
        itemsInCart.put(item, itemsInCart.getOrDefault(item, 0) + amount);
        itemIdMap.put(item.getId(), item);
    }
    public void removeItemById(int[] idAndAmount) {
        Item item = getItemById(idAndAmount[0]);
        if (item != null) {
            // Remove item from cart
            removeItem(item, idAndAmount[1]);
        } else {
            System.out.printf("Artikel mit ID %d nicht im Warenkorb gefunden%n", idAndAmount[1]);
        }
    }
    private void removeItem(Item item, int amount) {
        Integer currentAmount = itemsInCart.get(item);
        if (currentAmount != null) {
            if (amount < currentAmount) {
                itemsInCart.put(item, currentAmount - amount);
                inventory.putItemBackInInventory(new int[]{item.getId(), amount});
            } else {
                // Wenn der zu entfernende Betrag größer oder gleich der aktuellen Menge ist, entfernen Sie das Element ganz
                itemsInCart.remove(item);
            }
        } else {
            System.out.printf("Artikel %s nicht in Warenkorb gefunden%n", item);
        }
    }
    public double getTotalCost() {
        double totalCost = 0.0;
        for (Item item : itemsInCart.keySet()) {
            totalCost += item.getPrice() * itemsInCart.get(item);
        }
        return totalCost;
    }
    public int getItemCount() {
        return itemsInCart.values().stream()
                .reduce(0, Integer::sum);
    }
    public void putCartBackToInventory() {
        List<Map.Entry<Item, Integer>> entries = new ArrayList<>(itemsInCart.entrySet());
        for (Map.Entry<Item, Integer> entry : entries) {
            Item item = entry.getKey();
            Integer amount = entry.getValue();
            removeItem(item, amount);
        }
        emptyCart();
    }
    public boolean contains(Item item) {
        return itemsInCart.containsKey(item);
    }
    public void printCart() {
        for (Item item : itemsInCart.keySet()) {
            System.out.printf("%5d %-15s %.2f€ \n", item.getId(), item.getName(), item.getPrice() * itemsInCart.get(item));
        }
    }
    public void fillCart(int[] idAndAmount){
        addItem(inventory.fillCartFromInventory(idAndAmount),idAndAmount[1]);
    }
    public Map<Item, Integer> getItemsInCart() {
        return itemsInCart;
    }
    public List<CartItem> getItemsFromCart() {
        List<CartItem> cartItems = new ArrayList<>();

        for (Map.Entry<Item, Integer> entry : itemsInCart.entrySet()) {
            CartItem cartItem = new CartItem(entry.getKey(), entry.getValue());
            cartItems.add(cartItem);
        }

        return cartItems;
    }
    private void emptyCart(){
        itemsInCart.clear();
        itemIdMap.clear();
    }
    public void sellCart(){
        inventory.writeInventoryToDatabase();
        invoice.generateInvoice();
        // todo : creating Invoice and write to DB
        emptyCart();
    }

}
