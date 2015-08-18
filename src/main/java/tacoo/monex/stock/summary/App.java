package tacoo.monex.stock.summary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.RandomStringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class App {
    public static void main(String[] args) {
        new App().execute();
    }

    void execute() {
        Properties properties = loadProperties();
        String id = properties.get("monex.id").toString();
        String pass = properties.get("monex.password").toString();
        fetchCurrentYearSummary(id, pass);
    }

    private void fetchCurrentYearSummary(String id, String pass) {
        FirefoxProfile profile = setupProfile();
        FirefoxDriver driver = new FirefoxDriver(profile);
        driver.get("http://www.monex.co.jp/");
        WebElement loginElement = driver.findElement(By.xpath("//a[contains(@href, 'login')]"));
        loginElement.click();
        wait(driver, "//input[@name='loginid']");
        WebElement idElement = driver.findElement(By.xpath("//input[@name='loginid']"));
        idElement.sendKeys(id);
        WebElement passwdElement = driver.findElement(By.xpath("//input[@name='passwd']"));
        passwdElement.sendKeys(pass);
        passwdElement.sendKeys(Keys.ENTER);
        wait(driver, "//a[contains(@href, 'zan_sykai')]");
        driver.findElement(By.xpath("//a[contains(@href, 'zan_sykai')]")).click();
        wait(driver, "//a[contains(@href, 'sisan/rireki')]");
        driver.findElement(By.xpath("//a[contains(@href, 'sisan/rireki')]")).click();
        wait(driver, "//a[contains(@href, 'sisan/torireki/all')]");
        driver.findElement(By.xpath("//a[contains(@href, 'sisan/torireki/all')]")).click();

        Set<String> windowId = driver.getWindowHandles();
        Iterator<String> itererator = windowId.iterator();
        String mainWinID = itererator.next();
        String newAdwinID = itererator.next();

        driver.switchTo().window(newAdwinID);
        wait(driver, "//input[@name='TRADE_DATE_TYPE']");
        driver.findElement(By.xpath("//input[@name='TRADE_DATE_TYPE' and @value='1']")).click();

        WebElement fromMonthElement = driver.findElement(By.name("FROM_MONTH"));
        Select fromMonthSelect = new Select(fromMonthElement);
        fromMonthSelect.selectByValue("1");
        WebElement fromDayElement = driver.findElement(By.name("FROM_DAY"));
        Select fromDaySelect = new Select(fromDayElement);
        fromDaySelect.selectByValue("1");

        WebElement downloadElement = driver.findElement(By.name("SUBMIT"));
        downloadElement.click();

        File csvFile = Stream.of(new File(tempDirPath).listFiles())
                .filter(f -> f.getName().endsWith(".csv"))
                .findFirst()
                .get();
        System.out.println("csv file: " + csvFile.getAbsolutePath());

        loadCsv(csvFile);

        csvFile.delete();
        new File(tempDirPath).delete();
        driver.close();
        driver.switchTo().window(mainWinID);
        driver.close();
    }

    private void loadCsv(File csvFile) {
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
                        return new MonthlySummary(calendar.get(Calendar.YEAR) + "", (calendar.get(calendar.MONTH) + 1) + "", category, intprice);
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
            total.keySet().stream().sorted().forEach(key->{
                System.out.println(String.format("total: category=%s, amount=%d", key, total.get(key)));
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    String tempDirPath;

    private FirefoxProfile setupProfile() {
        FirefoxProfile firefoxProfile = new FirefoxProfile();
        firefoxProfile.setPreference("browser.download.folderList", 2);
        firefoxProfile.setPreference("browser.download.manager.showWhenStarting", false);
        tempDirPath = System.getProperty("java.io.tmpdir") + "/" + RandomStringUtils.randomAlphabetic(6) + "/";
        System.out.println("temp dir: " + tempDirPath);
        File baseDir = new File(tempDirPath);
        if (!baseDir.mkdirs()) {
            throw new RuntimeException("faild to create temp dir:" + tempDirPath);
        }
        firefoxProfile.setPreference("browser.download.dir", baseDir.getAbsolutePath());
        firefoxProfile.setPreference("browser.helperApps.neverAsk.saveToDisk", "application/csv,text/csv,application/x-csv;");
        return firefoxProfile;
    }

    private void wait(FirefoxDriver driver, String xpath) {
        WebDriverWait wait = new WebDriverWait(driver, 30);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
    }

    private Properties loadProperties() {
        InputStream resourceAsStream = getClass().getResourceAsStream("/monex.properties");
        Properties properties = new Properties();
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties;
    }

    class MonthlySummary {
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
            result = prime * result + getOuterType().hashCode();
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
            if (!getOuterType().equals(other.getOuterType()))
                return false;
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

        private App getOuterType() {
            return App.this;
        }
    }
}
