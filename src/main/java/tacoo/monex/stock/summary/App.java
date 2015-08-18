package tacoo.monex.stock.summary;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class App {
	public static void main(String[] args) {
		new App().sendMail();
	}

	void sendMail() {
		Properties properties = loadProperties();
		System.out.println(properties.get("monex.id"));
		System.out.println(properties.get("monex.password"));
	}

	private Properties loadProperties() {
		InputStream resourceAsStream = getClass().getResourceAsStream(
				"/monex.properties");
		Properties properties = new Properties();
		try {
			properties.load(resourceAsStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return properties;
	}
}
