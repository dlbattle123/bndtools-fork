package biz.aQute.bnd.reporter.plugins;

import static org.junit.Assert.assertArrayEquals;

import aQute.lib.io.IO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import junit.framework.TestCase;

public class XsltTransformerPluginTest extends TestCase {
	
	public void testXsltTransformer()
	throws Exception {
		
		final XsltTransformerPlugin t = new XsltTransformerPlugin();
		final Map<String, Object> report = new HashMap<>();
		final Map<String, String> parameters = new HashMap<>();
		report.put("test", "test");
		parameters.put("param1", "param");
		
		final ByteArrayOutputStream model = new ByteArrayOutputStream();
		new XmlReportSerializerPlugin().serialize(report, model);
		
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		
		t.transform(IO.stream(model.toByteArray()), IO.stream(new File("testresources/xslt.xslt")), output, parameters);
		
		assertTrue(new String(output.toByteArray()).contains("test"));
		
		assertArrayEquals(t.getHandledModelExtensions(), new String[] {
			"xml"
		});
		assertArrayEquals(t.getHandledTemplateExtensions(), new String[] {
			"xslt",
			"xsl"
		});
	}
}
