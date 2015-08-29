package tacoo.monex.stock.summary;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

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
        boolean all = true;
        if (args.length == 1 && "year".equals(args[0])) {
            all = false;
        }
        new App().execute(all);
    }

    void execute(boolean printAll) {
        Properties properties = loadProperties();
        String id = properties.get("monex.id").toString();
        String pass = properties.get("monex.password").toString();
        File csvFile = fetchCurrentYearSummary(id, pass);
        if (printAll) {
            AllPrint.printSummary(csvFile);
        } else {
            YearlyPrint.printSummary(csvFile);
        }
        csvFile.delete();
        new File(tempDirPath).delete();
    }

    private File fetchCurrentYearSummary(String id, String pass) {
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

        WebElement fromYearElement = driver.findElement(By.name("FROM_YEAR"));
        Select fromYearSelect = new Select(fromYearElement);
        fromYearSelect.selectByValue("1999");

        WebElement fromMonthElement = driver.findElement(By.name("FROM_MONTH"));
        Select fromMonthSelect = new Select(fromMonthElement);
        fromMonthSelect.selectByValue("1");
        WebElement fromDayElement = driver.findElement(By.name("FROM_DAY"));
        Select fromDaySelect = new Select(fromDayElement);
        fromDaySelect.selectByValue("1");

        WebElement downloadElement = driver.findElement(By.name("SUBMIT"));
        downloadElement.click();

        File csvFile = null;
        for (int i = 0; i < 30; i++) {
            Optional<File> first = Stream.of(new File(tempDirPath).listFiles())
                    .filter(f -> f.getName().endsWith(".csv"))
                    .findFirst();
            if (first.isPresent()) {
                csvFile = first.get();
                break;
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("csv file: " + csvFile.getAbsolutePath());

        driver.close();
        driver.switchTo().window(mainWinID);
        driver.close();

        return csvFile;
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

}
