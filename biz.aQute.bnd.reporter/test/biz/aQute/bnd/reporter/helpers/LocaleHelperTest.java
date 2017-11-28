package biz.aQute.bnd.reporter.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import aQute.bnd.osgi.Jar;
import org.junit.Test;

public class LocaleHelperTest {
	
	@Test
	public void testEmpty() {
		final LocaleHelper h = LocaleHelper.empty();
		
		assertEquals("test", h.get("test"));
		assertEquals("", h.get(""));
		assertNull(h.get("%test"));
		assertNull(h.get(null));
	}
	
	@Test
	public void testNotFound() {
		final Jar jar = new Jar("jar");
		assertNull(LocaleHelper.get(jar, "", "ln/bundle"));
		
		jar.putResource("ln/bundle.properties", new PropResource());
		assertNull(LocaleHelper.get(jar, "", "ln/other"));
	}
	
	@Test
	public void testUnlocalized() {
		final Jar jar = new Jar("jar");
		final PropResource r = new PropResource();
		r.add("key", "value");
		jar.putResource("ln/bundle.properties", r);
		
		final LocaleHelper h = LocaleHelper.get(jar, "", "ln/bundle");
		
		assertEquals("key", h.get("key"));
		assertEquals("value", h.get("%key"));
		assertNull(h.get("%  key"));
		assertNull(h.get("%test"));
	}
	
	@Test
	public void testLocalized() {
		final Jar jar = new Jar("jar");
		
		jar.putResource("ln/ignored.properties", new PropResource());
		
		final PropResource r = new PropResource();
		r.add("key", "valueUN");
		r.add("key2", "value2UN");
		r.add("key3", "value3UN");
		jar.putResource("ln/bundle.properties", r);
		
		final PropResource rEN = new PropResource();
		rEN.add("key", "valueEN");
		rEN.add("key2", "value2EN");
		jar.putResource("ln/bundle_en.properties", rEN);
		
		final PropResource rENUS = new PropResource();
		rENUS.add("key2", "value2ENUS");
		jar.putResource("ln/bundle_en_US.properties", rENUS);
		
		final PropResource rENUSVAR = new PropResource();
		rENUSVAR.add("key3", "value3ENUSVAR");
		rENUSVAR.add("key4", "value4ENUSVAR");
		jar.putResource("ln/bundle_en_US_var.properties", rENUSVAR);
		
		final LocaleHelper h = LocaleHelper.get(jar, "en", "ln/bundle");
		
		assertEquals("key", h.get("key"));
		assertEquals("valueEN", h.get("%key"));
		assertEquals("value2EN", h.get("%key2"));
		assertEquals("value3UN", h.get("%key3"));
		assertNull(h.get("%key4"));
		
		assertEquals("valueEN", h.get("%key", "en_US"));
		assertEquals("value2ENUS", h.get("%key2", "en_US"));
		assertEquals("value3UN", h.get("%key3", "en_US"));
		assertNull(h.get("%key4", "en_US"));
		
		assertEquals("valueEN", h.get("%key", "en_US_var"));
		assertEquals("value2ENUS", h.get("%key2", "en_US_var"));
		assertEquals("value3ENUSVAR", h.get("%key3", "en_US_var"));
		assertEquals("value4ENUSVAR", h.get("%key4", "en_US_var"));
		
		assertEquals("valueUN", h.get("%key", ""));
		assertEquals("value2UN", h.get("%key2", ""));
		assertEquals("value3UN", h.get("%key3", ""));
		assertNull(h.get("%key4", ""));
		
		assertNull(h.get("%  key"));
		assertNull(h.get("%test"));
		
		assertNull(h.get("%key", "en_US_var_bir"));
	}
}
