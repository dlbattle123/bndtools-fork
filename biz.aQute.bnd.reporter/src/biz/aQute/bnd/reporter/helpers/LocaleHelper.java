package biz.aQute.bnd.reporter.helpers;

import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Resource;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;

/**
 * An helper to extract localization data from a Jar.
 */
public class LocaleHelper {
	
	private final String _locale;
	private final Map<String, Map<String, String>> _localizations;
	
	private LocaleHelper(final Jar jar, final String locale, final String basePath) {
		_locale = locale;
		_localizations = extractLocalizations(jar, basePath);
	}
	
	private LocaleHelper() {
		_locale = "";
		_localizations = new HashMap<>();
	}
	
	/**
	 * Extracts the localization data of the Jar and return the helper.
	 *
	 * @param jar the jar containing the localization file, must not be {@code null}
	 * @param locale the default locale for this helper, must not be {@code null}
	 * @param basePath a path in the Jar to the localization property file without its extension and its locale suffix,
	 *            must not be {@code null}
	 * @return a {@code LocaleHelper} which contains localization data or {@code null} if the localization property file
	 *         is not found
	 */
	public static LocaleHelper get(final Jar jar, final String locale, final String basePath) {
		Objects.requireNonNull(jar, "jar");
		Objects.requireNonNull(locale, "locale");
		Objects.requireNonNull(basePath, "basePath");
		
		if (hasLocalization(jar, basePath)) {
			return new LocaleHelper(jar, locale, basePath);
		} else {
			return null;
		}
	}
	
	/**
	 * @return a {@code LocaleHelper} without localization data, never {@code null}
	 */
	public static LocaleHelper empty() {
		return new LocaleHelper();
	}
	
	/**
	 * If the argument is a variable, its corresponding value will be returned for the default locale of this helper
	 * instance. Otherwise, the argument is returned.
	 * <p>
	 * Values will be search from the most specific to the less specific locale including unlocalized (empty locale)
	 * value.
	 *
	 * @param variableOrValue a variable (starting with '%') or a value, can be {@code null}
	 * @return the localized value, can be {@code null}
	 */
	public String get(final String variableOrValue) {
		return get(variableOrValue, _locale);
	}
	
	/**
	 * If the argument is a variable, its corresponding value will be returned for the specified locale. Otherwise, the
	 * argument is returned.
	 * <p>
	 * Values will be search from the most specific to the less specific locale including unlocalized (empty locale)
	 * value.
	 *
	 * @param variableOrValue a variable (starting with '%') or a value, can be {@code null}
	 * @param locale the locale, must not be {@code null}
	 * @return the localized value, can be {@code null}
	 */
	public String get(final String variableOrValue, final String locale) {
		Objects.requireNonNull(locale, "locale");
		
		if (variableOrValue != null) {
			if (variableOrValue.startsWith("%")) {
				String nextLocale = locale;
				final String variable = variableOrValue.substring(1);
				String result = null;
				
				do {
					if (_localizations.get(nextLocale) != null) {
						result = _localizations.get(nextLocale).get(variable);
					}
					nextLocale = computeNextLocale(nextLocale);
				} while (nextLocale != null && result == null);
				
				return result;
			} else {
				return variableOrValue;
			}
		} else {
			return null;
		}
	}
	
	private String computeNextLocale(final String nextLocale) {
		if (nextLocale == "") {
			return null;
		}
		
		final String[] localePart = nextLocale.split("_");
		
		if (localePart.length == 1) {
			return "";
		}
		
		if (localePart.length == 2) {
			return localePart[0];
		}
		
		if (localePart.length == 3) {
			return localePart[0] + "_" + localePart[1];
		}
		
		return null;
	}
	
	private static boolean hasLocalization(final Jar jar, final String path) {
		for (final Entry<String, Resource> entry : jar.getResources().entrySet()) {
			if (entry.getKey().startsWith(path)) {
				return true;
			}
		}
		return false;
	}
	
	private Map<String, Map<String, String>> extractLocalizations(final Jar jar, final String path) {
		final Map<String, Map<String, String>> result = new HashMap<>();
		for (final Entry<String, Resource> entry : jar.getResources().entrySet()) {
			if (entry.getKey().startsWith(path)) {
				String lang = entry.getKey().substring(path.length()).replaceFirst("\\..*", "");
				if (lang.startsWith("_")) {
					lang = lang.substring(1);
				}
				
				try (InputStream inProp = entry.getValue().openInputStream()) {
					final Properties prop = new Properties();
					prop.load(inProp);
					
					final Map<String, String> properties = new HashMap<>();
					for (final String key : prop.stringPropertyNames()) {
						properties.put(key, prop.getProperty(key));
					}
					
					result.put(lang, properties);
				} catch (final Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		return result;
	}
}
