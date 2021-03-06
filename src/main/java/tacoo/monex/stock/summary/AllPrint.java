package tacoo.monex.stock.summary;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class AllPrint {

    private static final String YYYY_MM_DD = "yyyy/MM/dd";
    static String baseDate = "受渡日";

    public static void main(String[] args) {
        AllPrint.printSummary(new File("19990101-20151229.csv"));
    }

    static Set<String> creditIn = set("ご入金", "口座振替（外国株口座から）", "口座振替（ＦＸＰＬＵＳ口座から）", "振替（信用保証金から）");
    static Set<String> creditOut = set("口座振替（外国株口座へ）", "口座振替（ＦＸＰＬＵＳ口座へ）", "振替（信用保証金へ）");
    static Set<String> tax = set("源泉徴収税（所得税）", "源泉徴収税（住民税）");
    static Set<String> taxReturn = set("還付金（所得税）", "還付金（住民税）");
    static Set<String> yield = set("ＭＲＦ再投資", "ＭＲＦ再投資（一般）", "ＭＲＦ再投資（特定）", "配当金");

    public static void printSummary(File csvFile) {
        try (CSVParser parser = CSVFormat.EXCEL.parse(new InputStreamReader(
                new FileInputStream(csvFile), "sjis"))) {

            final Map<String, Integer> headerMap = new HashMap<>();
            Map<String, Money> sums = new HashMap<>();
            List<CSVRecord> csvDataList = new ArrayList<>();
            filterHeaderAndMRF(parser, headerMap, csvDataList);
            csvDataList.sort(new CSVRecordComparator(headerMap));

            String year = "";
            SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD);
            DecimalFormat df = new DecimalFormat("###,###.###");
            Map<String, Stock> stocks = new HashMap<>();

            Map<String, String> stockCodeAndName = new HashMap<>();

            for (CSVRecord r : csvDataList) {
                Date date = sdf.parse(r.get(headerMap.get(baseDate)));
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                String tmp = calendar.get(Calendar.YEAR) + "";
                if (!"".equals(year) && !tmp.equals(year)) {
                    Money lastOne = sums.get(year);
                    Map<String, Stock> carryOverStocks = new HashMap<>();
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

                if (creditIn.contains(tradeType)) {
                    money.credit += df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                } else if (creditOut.contains(tradeType)) {
                    money.credit -= df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                } else if (tax.contains(tradeType)) {
                    int tax = -df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                    money.incomeTax += tax;
                } else if (taxReturn.contains(tradeType)) {
                    int tax = df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                    money.incomeTax += tax;
                } else if (yield.contains(tradeType)) {
                    money.yield += df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                } else if ("お買付".equals(tradeType)) {
                    int value = df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                    int qty = df.parse(r.get(headerMap.get("数量（株/口）/返済数量"))).intValue();

                    int salesTax1 = df.parse(r.get(headerMap.get("手数料"))).intValue();
                    int salesTax2 = df.parse(r.get(headerMap.get("税金(手数料消費税及び譲渡益税)"))).intValue();

                    money.salesTaxFee += salesTax1;
                    money.salesTaxFee += salesTax2;

                    String stockCode = r.get(headerMap.get("銘柄コード")).trim();
                    String stockName = r.get(headerMap.get("銘柄名")).trim();
                    stockCodeAndName.put(stockCode, stockName);
                    Stock stock = stocks.get(stockCode);
                    if (stock == null) {
                        stock = new Stock();
                        stocks.put(stockCode, stock);
                    }
                    int ave = ave(stock.total, stock.quantity, value, qty);
                    stock.name = stockCode + "-" + stockName;
                    stock.quantity += qty;
                    stock.total -= value;
                    stock.averagePrice = ave;
                } else if ("ご売却".equals(tradeType)) {
                    int value = df.parse(r.get(headerMap.get("受渡金額(円)"))).intValue();
                    int qty = df.parse(r.get(headerMap.get("数量（株/口）/返済数量"))).intValue();

                    int salesTax1 = df.parse(r.get(headerMap.get("手数料"))).intValue();
                    int salesTax2 = df.parse(r.get(headerMap.get("税金(手数料消費税及び譲渡益税)"))).intValue();

                    money.salesTaxFee += salesTax1;
                    money.salesTaxFee += salesTax2;

                    String stockCode = r.get(headerMap.get("銘柄コード")).trim();
                    String stockName = r.get(headerMap.get("銘柄名")).trim();
                    stockCodeAndName.put(stockCode, stockName);
                    Stock stock = stocks.get(stockCode);
                    if (stock == null) {
                        stock = new Stock();
                        stocks.put(stockCode, stock);
                    }
                    stock.name = stockCode + "-" + stockName;

                    int basePrice = stock.averagePrice * qty;
                    stock.quantity -= qty;
                    stock.total += basePrice;
                    money.taxableGain += value - basePrice;

                    if (stock.quantity == 0) {
                        money.nonTaxableGain += stock.total;
                        stocks.remove(stockCode);
                    }
                } else {
                    System.out.println("unknown record found: " + r);
                }
            }

            Money lastOne = sums.get(year);
            Map<String, Stock> carryOverStocks = new HashMap<>();
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
            System.out.println(String.format("%7s%13s%13s%13s%13s%13s%13s%13s"
                    , "Year"
                    , "Credit"
                    , "Gain"
                    , "Gain(NoTax)"
                    , "Yield"
                    , "SalesTax/Fee"
                    , "IncomeTax"
                    , "Earned"));
            int totalCredit = 0;
            int totalGain = 0;
            int totalNoTaxGain = 0;
            int totalYeild = 0;
            int totalSalesTax = 0;
            int totalIncomeTax = 0;
            for (String k : keys) {
                Money money = sums.get(k);
                System.out.println(String.format("%7s%13d%13d%13d%13d%13s%13d%13d"
                        , k
                        , money.credit
                        , money.taxableGain
                        , money.nonTaxableGain
                        , money.yield
                        , "(" + money.salesTaxFee + ")"
                        , money.incomeTax
                        , money.taxableGain + money.nonTaxableGain + money.yield - money.incomeTax));

                totalCredit += money.credit;
                totalGain += money.taxableGain;
                totalNoTaxGain += money.nonTaxableGain;
                totalYeild += money.yield;
                totalSalesTax += money.salesTaxFee;
                totalIncomeTax += money.incomeTax;
            }
            System.out.println();
            System.out.println("Current Stocks");
            System.out.println(String.format("%8s%13s%10s"
                    , "Quantity"
                    , "Amount"
                    , "Name"));

            Money money = sums.get(keys[keys.length - 1]);
            int totalStock = 0;
            for (String id : money.stocks.keySet()) {
                Stock stock = money.stocks.get(id);
                System.out.println(String.format("%8d%13d   %s", stock.quantity, stock.total, stock.name));
                totalStock += stock.total;
            }
            System.out.println();
            System.out.println("Total");
            System.out.println(String.format("%13s%13s%13s%13s%13s%13s"
                    , "Credit"
                    , "Gain"
                    , "Gain(NoTax)"
                    , "Yield"
                    , "SalesTax/Fee"
                    , "IncomeTax"));
            System.out.println(String.format("%13d%13d%13d%13d%13s%13d"
                    , totalCredit
                    , totalGain
                    , totalNoTaxGain
                    , totalYeild
                    , "(" + totalSalesTax + ")"
                    , totalIncomeTax));
            System.out.println();

            double doubleValue = BigDecimal.valueOf(totalGain + totalNoTaxGain + totalYeild - totalIncomeTax)
                    .divide(BigDecimal.valueOf(totalCredit), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            System.out.println(String.format("%-20s%,.2f%%", "Performance:", doubleValue));
            System.out.println(String.format("%-20s%13d", "Total Stock Amount:", totalStock));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static Set<String> set(String... ss) {
        HashSet<String> set = new HashSet<String>();
        for (String s : ss) {
            set.add(s);
        }
        return set;
    }

    private static int ave(int baseValue, int baseQty, int addedValue, int addedQty) {
        int totalValue = Math.abs(baseValue) + Math.abs(addedValue);
        int totalQty = Math.abs(baseQty) + Math.abs(addedQty);
        return BigDecimal.valueOf(totalValue).divide(BigDecimal.valueOf(totalQty), 0, RoundingMode.UP).intValue();
    }

    private static void filterHeaderAndMRF(CSVParser parser, final Map<String, Integer> headerMap, List<CSVRecord> csvDataList) {
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

    private static final class CSVRecordComparator implements Comparator<CSVRecord> {
        private final SimpleDateFormat sdf = new SimpleDateFormat(YYYY_MM_DD);
        private final Map<String, Integer> headerMap;

        private CSVRecordComparator(Map<String, Integer> headerMap) {
            this.headerMap = headerMap;
        }

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
    }

    static class Money {

        int credit = 0;
        int yield = 0;
        int incomeTax = 0;
        int salesTaxFee = 0;
        int taxableGain = 0;
        int nonTaxableGain = 0;
        Map<String, Stock> stocks = new HashMap<String, Stock>();

        @Override
        public String toString() {
            return "Money [credit=" + credit + ", yield=" + yield + ", incomeTax=" + incomeTax + ", salesTaxFee=" + salesTaxFee + ", taxableGain=" + taxableGain
                    + ", nonTaxableGain=" + nonTaxableGain + ", stocks=" + stocks + "]";
        }
    }

    static class Stock {

        String name;
        int quantity;
        int total;
        int averagePrice;

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
            return "Stock [name=" + name + ", quantity=" + quantity + ", total=" + total + ", averagePrice=" + averagePrice + "]";
        }

        public Stock clone() {
            Stock stock = new Stock();
            stock.name = this.name;
            stock.quantity = this.quantity;
            stock.total = this.total;
            stock.averagePrice = this.averagePrice;
            return stock;
        }
    }
}
