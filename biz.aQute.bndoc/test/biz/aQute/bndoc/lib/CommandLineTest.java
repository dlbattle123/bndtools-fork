package biz.aQute.bndoc.lib;

import junit.framework.*;
import biz.aQute.bndoc.main.*;

public class CommandLineTest extends TestCase {

	public void testBase() throws Exception {
		Main.main(new String[] {"-etb", "testdocs/docs", "html","--clean","--resources","tmp","-p", "bndoc.bndoc","-o","tmp/tmp.html","en"});
	}
}
