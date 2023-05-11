package PoS_Main;

import java.sql.*;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

public class PoS_MainSQL {

  private static final String DB_URL =
    "jdbc:sqlite::resource:PoS_SQLite.sqlite";
  private static DatabaseConnection dbConnection;
  private static final int maxDisplayItems = 16; //idealerweise wird vor Programm Beginn abgefragt wie viele Zeilen die Datenbank hat und anschließen der Wert aller Zeilen als Wert gesetzt
  private static final int maxBuyingItems = 20; //willkürlich, kann erhöht werden
  private static final ArrayList<Items> itemArrayList = new ArrayList<>(); //array für das Inventar
  private static int totalSQLItems = 0; //Counter für die Anzahl der items im Array
  private static double totalPrice = 0; //Akkumulierter Preis des Einkaufs
  private static int countOfItems = 0; //Laufvariable die zur Iteration durch das Array funktioniert und am Ende die Menge der Artikel selbst ausgibt

  public static void main(String[] args) {
    Scanner in = new Scanner(System.in);
    String input;
    String[] boughtArrayToSQL = new String[maxBuyingItems];
    String[] receiptItems = new String[maxBuyingItems];

    char exit = 'j'; // while Schleife um das Programm immer wieder von vorne starten zu lassen. Lässt sich sicher eleganter lösen, Lösung noch nicht bekannt

    while (exit != 'n') {
      dbConnection = new DatabaseConnection(DB_URL);
      clearScreen();
      System.out.println("Gib die <ID> der Gruppe ein");
      group_choose();
      System.out.print(
        """
                    Gib '<Getränk> <Menge>' ein, die verkauft werden soll gefolgt von 'Enter'
                    Wähle die Artikel Gruppe neu mit 'gruppen'
                    Wenn du fertig bist, schreibe 'fertig' um den Einkauf zu beenden\s
                    """
      );
      printInventory();

      totalPrice = 0;
      countOfItems = 0; //Neuer Kauf, keine Artikel
      while (!(input = in.nextLine()).equals("fertig")) {
        if (input.equals("gruppen")) {
          group_choose();
          printInventory();
        }
        String[] tokens = input.split(" "); //Auftrennung des Strings in einzelne Strings zur Weiterverarbeitung

        if (tokens.length > 1) { //umgeht eine Exception bei Ausbruch durch 'fertig'
          String name = tokens[0];
          tokens[1] = tokens[1].replaceAll(",", ".");
          double amount = Double.parseDouble(tokens[1]);
          for (int count = 0; count < totalSQLItems; count++) {
            if (itemArrayList.get(count).getName().equalsIgnoreCase(name)) {
              receiptItems[countOfItems] =
                String.format(
                  "%-19s %.2f€ %-12s %.2f",
                  name,
                  itemArrayList.get(count).getPrice(),
                  "",
                  amount
                );
              boughtArrayToSQL[countOfItems] =
                String.format(
                  "%d %.2f %s %.2f %.2f",
                  itemArrayList.get(count).getId(),
                  itemArrayList.get(count).getStock() - amount,
                  name,
                  itemArrayList.get(count).getPrice(),
                  amount
                );
              itemArrayList.get(count).decreaseStock(amount);
              totalPrice += amount * itemArrayList.get(count).getPrice();
              countOfItems++;
              break;
            }
          }
        }
      }
      System.out.printf("Die Gesamtkosten betragen: %.2f \n", totalPrice);
      System.out.println("Wollen sie den Einkauf tätigen? (J/N) ");

      if (stringToChar() == 'j') {
        updateInventory(boughtArrayToSQL);
        exportInvoice(boughtArrayToSQL);
        System.out.println("\n Inventar wurde aktualisiert"); // hier würde dann der neue Wert in die Datenbank geschrieben
        System.out.println("~~~~~~~~~~~~~~~~~");

        System.out.println("\n Möchten sie eine Quittung haben? (j/n)");
        if (stringToChar() == 'j') {
          printReceipt(receiptItems);
        }
      } else {
        System.out.println("Inventar wurde nicht belastet"); //hier müsste der gesamte Vorgang verworfen werden
      }
      System.out.println("Nächster kauf? (j/n)");
      exit = stringToChar();
    }
  }

  public static void exportInvoice(String[] Einkauf) {
    String invoiceNr = "000000/00";
    String[] invoiceArray;
    int invoiceYear = YearMonth.now().getYear();
    int integerInvoiceNumber;
    double price;
    double amountSold;

    try {
      Statement stmt = dbConnection.getConnection().createStatement();

      ResultSet rs = stmt.executeQuery(
        "SELECT bill_number FROM invoice WHERE invoice_id=" +
        "(SELECT seq FROM sqlite_sequence WHERE name='invoice')"
      );
      invoiceNr = rs.getString("bill_number");
      invoiceArray = invoiceNr.split("/");
      integerInvoiceNumber = Integer.parseInt(invoiceArray[0]) + 1;
      invoiceNr = String.format("%06d/%d", integerInvoiceNumber, invoiceYear);
      for (String invoice : Einkauf) {
        if (invoice == null) {
          break;
        }
        String[] exportInvoice = invoice.split(" ");
        exportInvoice[3] = exportInvoice[3].replaceAll(",", ".");
        exportInvoice[4] = exportInvoice[4].replaceAll(",", ".");

        String itemName = (exportInvoice[2]);
        amountSold = Double.parseDouble(exportInvoice[4]);
        price = Double.parseDouble(exportInvoice[3]) * amountSold;
        String query = String.format(
          Locale.US,
          "INSERT into invoice (item_name, amount, total_price, bill_cost, bill_number, storno)" +
          " VALUES ('%s', %.2f, %.2f, %.2f, '%s', FALSE);",
          itemName,
          amountSold,
          price,
          totalPrice,
          invoiceNr
        );
        stmt.executeUpdate(query);
        dbConnection.getConnection().commit();
      }
      stmt.close();
    } catch (Exception e) {
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
      //System.exit(0);
    }
  }

  public static void updateInventory(String[] Einkauf) {
    try {
      Statement stmt = dbConnection.getConnection().createStatement();
      String[] export;
      int itemsId;
      double stock;
      for (String i : Einkauf) {
        if (i == null) {
          break;
        }
        export = i.split(" ");
        itemsId = Integer.parseInt(export[0]);
        export[1] = export[1].replaceAll(",", ".");
        stock = Double.parseDouble(export[1]);
        String query =
          "UPDATE items set item_amount = " +
          stock +
          " where items_id =" +
          itemsId +
          ";";
        stmt.executeUpdate(query);
        dbConnection.getConnection().commit();
      }
      stmt.close();
    } catch (Exception e) {
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
    }
  }

  public static char stringToChar() {
    Scanner in = new Scanner(System.in);
    String charInput = in.next();
    charInput = charInput.toLowerCase();
    return charInput.charAt(0);
  }

  public static void printGroups() {
    try {
      Statement stmt = dbConnection.getConnection().createStatement();
      ResultSet rs = stmt.executeQuery(
        "SELECT groups_id, group_names FROM groups order by groups_id;"
      );
      System.out.print("ID  Gruppennamen \n");
      while (rs.next()) {
        int groups_id = rs.getInt("groups_id");
        String group_names = rs.getString("group_names");
        System.out.printf("%-3d %s \n", groups_id, group_names);
      }
      rs.close();
      stmt.close();
    } catch (Exception e) {
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
    }
  }

  public static void group_choose() {
    Scanner in = new Scanner(System.in);
    printGroups();
    System.out.println("Welche Gruppe? <ID>");
    int group_input = in.nextInt();
    getItems(group_input);
  }

  public static void printInventory() {
    for (int i = 0; i < totalSQLItems; i++) {
      System.out.println(itemArrayList.get(i).toString());
    }
  }

  public static void getItems(int group_input) {
    try {
      Statement stmt = dbConnection.getConnection().createStatement();
      ResultSet rs = stmt.executeQuery(
        "SELECT i.items_id, i.item_name, g.measurement, i.item_price, i.item_amount, g.group_names " +
        "FROM items i " +
        "JOIN groups g " +
        "on i.groups_id = g.groups_id " +
        "WHERE i.groups_id=" +
        group_input +
        ";"
      );
      totalSQLItems = 0;

      while (rs.next() && totalSQLItems <= maxDisplayItems) {
        int items_id = rs.getInt("items_id");
        String item_name = rs.getString("item_name");
        String measurement = rs.getString("measurement");
        double item_price = rs.getDouble("item_price");
        double stock = rs.getDouble("item_amount");
        String group_names = rs.getString("group_names");

        itemArrayList.add(
          new Items(
            items_id,
            item_name,
            measurement,
            item_price,
            stock,
            group_names
          ));
        totalSQLItems++;
      }
      rs.close();
      stmt.close();
    } catch (Exception e) {
      System.err.println(e.getClass().getName() + ": " + e.getMessage());
    }
  }

  public static void printReceipt(String[] receiptItems) {
    System.out.println("\n\n");
    System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    System.out.println("           		   Super Bar\n");
    System.out.println("                   Quittung");
    System.out.println("                  ~~~~~~~~~\n");
    System.out.println(
      "Artikel        " + "Preis pro Einheit       " + "Menge"
    );
    System.out.println("______________________________________________\n");
    for (int i = 0; i < countOfItems; i++) {
      System.out.println(receiptItems[i]);
    }
    System.out.println("\nMenge der Artikel insgesamt : " + countOfItems);
    System.out.printf("\nGesamtbetrag: %.2f€ \n", totalPrice);
    System.out.println("\n               Ende der Quittung");
    System.out.println("\nVielen Dank für Ihren Besuch");
    System.out.println("             Kommen Sie gerne wieder");
    System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
  }

  public static void clearScreen() {
    System.out.print("\033[H\033[2J");
    System.out.flush();
  }
}
