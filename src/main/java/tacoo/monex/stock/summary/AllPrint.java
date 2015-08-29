package tacoo.monex.stock.summary;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

public class AllPrint {

    static String baseDate = "受渡日";

    // static String baseDate = "約定日";

    public static void printSummary(File csvFile) {
        try (CSVParser parser = CSVFormat.EXCEL.parse(new InputStreamReader(
                new FileInputStream(csvFile), "sjis"))) {

            final Map<String, Integer> headerMap = new HashMap<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            DecimalFormat df = new DecimalFormat("###,###.###");
            Map<String, Money> sums = new HashMap<String, Money>();
            String year = "";
            Map<String, Stock> stocks = new HashMap<String, Stock>();
            List<CSVRecord> csvDataList = new ArrayList<>();
            for (CSVRecord r : parser) {
                if (r.getRecordNumber() == 1) {
                    continue;
                }
                if (r.getRecordNumber() == 2) {
                    headerMap.putAll(getHeader(r));
                } else {
                    String name = r.get(headerMap.get("銘柄名"));
                    if ("日興ＭＲＦ".equals(name)) {
                        continue;
                    }
                    csvDataList.add(r);
                }
            }
            csvDataList.sort(new Comparator<CSVRecord>() {
                @Override
                public int compare(CSVRecord r1, CSVRecord r2) {
                    try {
                        Date d1 = sdf.parse(r1.get(headerMap.get(baseDate)));
                        Date d2 = sdf.parse(r2.get(headerMap.get(baseDate)));
                        int comp1 = d1.compareTo(d2);
                        if (comp1 != 0) {
                            return comp1;
                        }
                        String t1 = r1.get(headerMap.get("取引"));
                        String t2 = r2.get(headerMap.get("取引"));
                        if (t1.equals("ご売却") && t2.equals("ご売却")) {
                            return 0;
                        } else if (t1.equals("ご売却")) {
                            return 1;
                        } else if (t2.equals("ご売却")) {
                            return -1;
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    return 0;
                }
            });

            for (CSVRecord r : csvDataList) {
                Date date = sdf.parse(r.get(headerMap.get(baseDate)));
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                String tmp = calendar.get(Calendar.YEAR) + "";
                if (!"".equals(year) && !tmp.equals(year)) {
                    Money lastOne = sums.get(year);
                    Map<String, Stock> carryOverStocks = new HashMap<String, Stock>();
                    for (String k : stocks.keySet()) {
                        Stock stock = stocks.get(k);
                        carryOverStocks.put(k, stock.clone());
                    }
                    lastOne.stocks = carryOverStocks;
                }
                year = calendar.get(Calendar.YEAR) + "";
                Money money = sums.get(year);
                if (money == null) {
                    money = new Money();
                    sums.put(year, money);
                }
                String tradeType = r.get(headerMap.get("取引"));

                if ("ご入金".equals(tradeType)
                        || "口座振替（外国株口座から）".equals(tradeType)) {
                    money.credit += df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                } else if ("口座振替（外国株口座へ）".equals(tradeType)) {
                    money.credit -= df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                } else if ("源泉徴収税（所得税）".equals(tradeType)
                        || "源泉徴収税（住民税）".equals(tradeType)) {
                    int tax = -df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                    money.incomeTax += tax;
                } else if ("還付金（所得税）".equals(tradeType)
                        || "還付金（住民税）".equals(tradeType)) {
                    int tax = df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                    money.incomeTax += tax;
                } else if ("ＭＲＦ再投資".equals(tradeType)
                        || "配当金".equals(tradeType)) {
                    money.yield += df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                } else if ("お買付".equals(tradeType)) {
                    int value = df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                    int qty = df.parse(r.get(headerMap.get("数量（株/口）/返済数量"))).intValue();

                    int salesTax1 = df.parse(r.get(headerMap.get("手数料"))).intValue();
                    int salesTax2 = df.parse(r.get(headerMap.get("税金(手数料消費税及び譲渡益税)"))).intValue();

                    value += salesTax1;
                    value += salesTax2;

                    money.salesTax += salesTax1;
                    money.salesTax += salesTax2;

                    String stockCode = r.get(headerMap.get("銘柄コード")).trim();
                    String stockName = r.get(headerMap.get("銘柄名")).trim();
                    String stockId = stockCode + " - " + stockName;
                    Stock stock = stocks.get(stockId);
                    if (stock == null) {
                        stock = new Stock();
                        stocks.put(stockId, stock);
                    }
                    stock.name = stockId;
                    stock.quantity += qty;
                    stock.total -= value;
                } else if ("ご売却".equals(tradeType)) {
                    int value = df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                    int qty = df.parse(r.get(headerMap.get("数量（株/口）/返済数量"))).intValue();

                    int salesTax1 = df.parse(r.get(headerMap.get("手数料"))).intValue();
                    int salesTax2 = df.parse(r.get(headerMap.get("税金(手数料消費税及び譲渡益税)"))).intValue();

                    value -= salesTax1;
                    value -= salesTax2;

                    money.salesTax += salesTax1;
                    money.salesTax += salesTax2;

                    String stockCode = r.get(headerMap.get("銘柄コード")).trim();
                    String stockName = r.get(headerMap.get("銘柄名")).trim();
                    String stockId = stockCode + " - " + stockName;
                    Stock stock = stocks.get(stockId);
                    if (stock == null) {
                        stock = new Stock();
                        stocks.put(stockId, stock);
                    }
                    stock.name = stockId;
                    if (stock.quantity == qty) {
                        money.gain += (stock.total + value);
                        stocks.remove(stockId);
                    } else {
                        // int ave = -stock.total / stock.quantity;
                        // int soldAve = value / qty;
                        // System.out.println(String.format("%s\t%s=%d", date,
                        // stockName, ((soldAve - ave) * qty)));
                        // money.gain += ((soldAve - ave) * qty);
                        stock.quantity -= qty;
                        // stock.total += value - ((soldAve - ave) * qty);
                        stock.total += value;
                    }
                } else {
                    System.out.println("unknown record found: " + r);
                }
            }

            Money lastOne = sums.get(year);
            Map<String, Stock> carryOverStocks = new HashMap<String, Stock>();
            for (String k : stocks.keySet()) {
                Stock stock = stocks.get(k);
                carryOverStocks.put(k, stock.clone());
            }
            lastOne.stocks = carryOverStocks;

            String[] keys = sums.keySet().toArray(new String[0]);
            Arrays.sort(keys, new Comparator<String>() {

                public int compare(String o1, String o2) {

                    return o1.compareTo(o2);
                }
            });
            System.out.println(String.format("%s%s%s%s%s%s"
                    , StringUtils.leftPad("Year", 7)
                    , StringUtils.leftPad("Credit", 13)
                    , StringUtils.leftPad("Gain", 13)
                    , StringUtils.leftPad("Yield", 13)
                    , StringUtils.leftPad("SalesTax", 13)
                    , StringUtils.leftPad("IncomeTax", 13)));
            int totalCredit = 0;
            int totalGain = 0;
            int totalYeild = 0;
            int totalSalesTax = 0;
            int totalIncomeTax = 0;
            for (String k : keys) {
                System.out.print(StringUtils.leftPad(k, 7));
                Money money = sums.get(k);
                System.out.print(StringUtils.leftPad("" + money.credit, 13));
                System.out.print(StringUtils.leftPad("" + money.gain, 13));
                System.out.print(StringUtils.leftPad("" + money.yield, 13));
                System.out.print(StringUtils.leftPad("" + money.salesTax, 13));
                System.out.println(StringUtils.leftPad("" + money.incomeTax, 13));
                totalCredit += money.credit;
                totalGain += money.gain;
                totalYeild += money.yield;
                totalSalesTax += money.salesTax;
                totalIncomeTax += money.incomeTax;
            }
            System.out.println();
            System.out.println("Current Stocks");
            System.out.println(String.format("%s%s\t%s"
                    , StringUtils.leftPad("Quantity", 8)
                    , StringUtils.leftPad("Amount", 13)
                    , "Name"));

            Money money = sums.get(keys[keys.length - 1]);
            int totalStock = 0;
            for (String id : money.stocks.keySet()) {
                Stock stock = money.stocks.get(id);
                System.out.println(StringUtils.leftPad("" + stock.quantity, 8)
                        + StringUtils.leftPad("" + stock.total, 13)
                        + "\t" + stock.name);
                totalStock += stock.total;
            }
            System.out.println();
            System.out.println("Total");
            System.out.println(String.format("%s%s%s%s%s"
                    , StringUtils.leftPad("Credit", 13)
                    , StringUtils.leftPad("Gain", 13)
                    , StringUtils.leftPad("Yield", 13)
                    , StringUtils.leftPad("SalesTax", 13)
                    , StringUtils.leftPad("IncomeTax", 13)));
            System.out.print(StringUtils.leftPad("" + totalCredit, 13));
            System.out.print(StringUtils.leftPad("" + totalGain, 13));
            System.out.print(StringUtils.leftPad("" + totalYeild, 13));
            System.out.print(StringUtils.leftPad("" + totalSalesTax, 13));
            System.out.println(StringUtils.leftPad("" + totalIncomeTax, 13));
            System.out.println();

            System.out.println(StringUtils.leftPad("Total Stock Amount:" + totalStock, 13));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static Map<String, Integer> getHeader(CSVRecord r) {

        Map<String, Integer> headerMap = new HashMap<String, Integer>();
        int i = 0;
        for (String k : r) {
            headerMap.put(k, i);
            i++;
        }
        return headerMap;
    }

    static class Money {

        int credit = 0;
        int yield = 0;
        int incomeTax = 0;
        int salesTax = 0;
        int gain = 0;
        Map<String, Stock> stocks = new HashMap<String, Stock>();

        @Override
        public String toString() {
            return "Money [credit=" + credit + ", yield=" + yield + ", incomeTax=" + incomeTax + ", salesTax=" + salesTax + ", gain=" + gain + ", stocks=" + stocks + "]";
        }

    }

    static class Stock {

        String name;
        int quantity;
        int total;

        @Override
        public int hashCode() {

            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {

            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Stock other = (Stock) obj;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }

        @Override
        public String toString() {

            return "Stock [name=" + name + ", quantity=" + quantity + ", total=" + total + "]";
        }

        public Stock clone() {

            Stock stock = new Stock();
            stock.name = this.name;
            stock.quantity = this.quantity;
            stock.total = this.total;
            return stock;
        }

    }

}
