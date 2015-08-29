package tacoo.monex.stock.summary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class YearlyPrint {
    
    public static void printSummary(File csvFile) {
        try (CSVParser parse = CSVFormat.EXCEL.parse(new InputStreamReader(
                new FileInputStream(csvFile), "sjis"))) {
            parse.iterator().next();
            CSVRecord header = parse.iterator().next();
            System.out.println(header);
            List<MonthlySummary> list = parse.getRecords()
                    .stream()
                    .filter(r -> r.size() == header.size())
                    .map(r -> {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                        String strdate = r.get(0);
                        Date date = null;
                        try {
                            date = sdf.parse(strdate);
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(date);
                        String category = r.get(4);
                        String price = r.get(12);
                        DecimalFormat numberFormat = new DecimalFormat("###,###.###");
                        int intprice = 0;
                        try {
                            intprice = numberFormat.parse(price).intValue();
                            if (category.equals("お買付")
                                    || calendar.equals("還付金（所得税）")
                                    || calendar.equals("還付金（住民税）")) {
                                intprice = -intprice;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (category.equals("還付金（所得税）")) {
                            category = "源泉徴収税（所得税）";
                        }
                        if (category.equals("還付金（住民税）")) {
                            category = "源泉徴収税（住民税）";
                        }
                        return new MonthlySummary(calendar.get(Calendar.YEAR) + "", (calendar.get(Calendar.MONTH) + 1) + "", category, intprice);
                    }).collect(Collectors.toList());
            Map<MonthlySummary, MonthlySummary> map = new HashMap<>();
            list.stream().forEach(sum -> {
                MonthlySummary curSum = map.get(sum);
                if (curSum == null) {
                    map.put(sum, sum);
                } else {
                    curSum.amount += sum.amount;
                }
            });
            Map<String, Integer> total = new HashMap<>();
            map.keySet().stream().sorted(new Comparator<MonthlySummary>() {
                @Override
                public int compare(MonthlySummary o1, MonthlySummary o2) {
                    int year = o1.year.compareTo(o2.year);
                    if (year != 0) {
                        return year;
                    }
                    int month = o1.month.compareTo(o2.month);
                    if (month != 0) {
                        return month;
                    }
                    return o1.category.compareTo(o2.category);
                }
            }).forEach(sum -> {
                Integer integer = total.get(sum.category);
                if (integer == null) {
                    integer = 0;
                }
                integer = integer.intValue() + sum.amount;
                total.put(sum.category, integer);
                System.out.println(sum);
            });
            total.keySet().stream().sorted().forEach(key -> {
                System.out.println(String.format("total: category=%s, amount=%d", key, total.get(key)));
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class MonthlySummary {
        String year;
        String month;
        String category;
        int amount;

        public MonthlySummary(String year, String month, String category, int amount) {
            super();
            this.year = year;
            this.month = month;
            this.category = category;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return "MonthlySummary [year=" + year + ", month=" + month + ", category=" + category + ", amount=" + amount + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((category == null) ? 0 : category.hashCode());
            result = prime * result + ((month == null) ? 0 : month.hashCode());
            result = prime * result + ((year == null) ? 0 : year.hashCode());
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
            MonthlySummary other = (MonthlySummary) obj;
            if (category == null) {
                if (other.category != null)
                    return false;
            } else if (!category.equals(other.category))
                return false;
            if (month == null) {
                if (other.month != null)
                    return false;
            } else if (!month.equals(other.month))
                return false;
            if (year == null) {
                if (other.year != null)
                    return false;
            } else if (!year.equals(other.year))
                return false;
            return true;
        }

    }

}
