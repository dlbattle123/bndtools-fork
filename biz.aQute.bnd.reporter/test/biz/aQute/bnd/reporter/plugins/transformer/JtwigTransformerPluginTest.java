package biz.aQute.bnd.reporter.plugins.transformer;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import aQute.lib.io.IO;
import biz.aQute.bnd.reporter.plugins.serializer.JsonReportSerializerPlugin;
import junit.framework.TestCase;

public class JtwigTransformerPluginTest extends TestCase {

	public void testJtwigTransformer() throws Exception {

		final JtwigTransformerPlugin t = new JtwigTransformerPlugin();
		final Map<String, Object> report = new HashMap<>();
		final Map<String, String> parameters = new HashMap<>();
		report.put("test", "test");
		parameters.put("param1", "param");

		final ByteArrayOutputStream model = new ByteArrayOutputStream();
		new JsonReportSerializerPlugin().serialize(report, model);

		final ByteArrayOutputStream output = new ByteArrayOutputStream();

		t.transform(IO.stream(model.toByteArray()),
				IO.stream(new File("testresources/transformer/jtwigTransformer.twig")), output, parameters);

		assertTrue(new String(output.toByteArray()).contains("test"));

		assertArrayEquals(t.getHandledModelExtensions(), new String[] { "json" });
		assertArrayEquals(t.getHandledTemplateExtensions(), new String[] { "twig", "jtwig" });
	}

	public void testJtwigTransformerDefault() throws Exception {

		final JtwigTransformerPlugin t = new JtwigTransformerPlugin();

		final ByteArrayOutputStream output = new ByteArrayOutputStream();

		Path testfile=Paths.get("testresources/metatypesEntry/result.json");
		t.transform(IO.stream(testfile),
				JtwigTransformerPlugin.class.getResourceAsStream("templates/library-bundle.twig"), output,
				new HashMap<String, String>());

		String test=new String(output.toByteArray());
		System.out.println(test);

	}
}
