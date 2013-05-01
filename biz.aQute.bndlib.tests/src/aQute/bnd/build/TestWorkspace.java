package aQute.bnd.build;

import java.io.*;
import java.util.*;

import junit.framework.*;

import aQute.bnd.build.*;

public class TestWorkspace  extends TestCase
{
		public void testDirectoryNotBSN() 
				throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/directoryNotBSN"));
			assertFalse(ws.projectNameIsDir);
		}
		
		public void testDirectoryBSN() 
				throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/ws"));
			assertTrue(ws.projectNameIsDir);
		}
		
		public void testNestedWorkspace()
				throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/gitWs/repo1"));
			assertEquals(4,ws.projectDirs.size());
			
			TreeSet<String> repos = new TreeSet<String>();
			for(File f : ws.projectDirs)
			{
				repos.add(f.getAbsolutePath());
			}
			Iterator<String> it = repos.iterator();
			assertTrue("Not test/gitWs/aDir ",it.next().endsWith("test/gitWs/aDir"));
			assertTrue("Not cnf/repo ",it.next().endsWith("test/gitWs/cnf.repo"));
			assertTrue("Not repo1 ",it.next().endsWith("test/gitWs/repo1"));
			assertTrue("Not repo2 ",it.next().endsWith("test/gitWs/repo2"));
		}
		
		public void testWorkspaceCached()
				throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/gitWs/repo1"));
			assertEquals(ws,Workspace.getWorkspace(new File("test/gitWs/repo1")));
		}
		
		public void testNonNestedWorkspace()
				throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/ws"));
			assertEquals(1,ws.projectDirs.size());
			assertTrue("should be test/ws "+ws.projectDirs.get(0).getAbsolutePath(),ws.projectDirs.get(0).getAbsolutePath().endsWith("test/ws"));
			
		}
		
		public void testAddProjectDirs_NoRedirect()
		{
			File baseDir = new File("test/ws");
			List<File> projectDirs = Workspace.getProjectDirs(baseDir, "/abc");
			assertEquals(0,projectDirs.size());
		}
		
		public void testAddProjectDirs_RedirectShort()
		{
			File baseDir = new File("test/gitWs/repo1/proj1");
			List<File> projectDirs = Workspace.getProjectDirs(baseDir, "..");
			assertEquals(1,projectDirs.size());
			assertTrue("Should be dummy "+projectDirs.get(0).getAbsolutePath(),projectDirs.get(0).getAbsolutePath().endsWith("test/gitWs/repo1/proj1/dummy"));
		}
		
		public void testAddProjectDirs_RedirectTwo()
		{
			File baseDir = new File("test/gitWs/repo1/proj1/dummy");
			List<File> projectDirs = Workspace.getProjectDirs(baseDir, "../..");
			assertEquals(1,projectDirs.size());
			assertTrue("Should be dummy "+projectDirs.get(0).getAbsolutePath(),projectDirs.get(0).getAbsolutePath().endsWith("test/gitWs/repo1/proj1/dummy"));
		}
		
		public void testGetBSNForProject_NameIsDir()
			throws Exception
		{
			Project p = Workspace.getProject(new File("test/gitWs/repo1/proj1"));
			assertEquals("proj1",p.getWorkspace().getBSNForProject(p));
		}
		
		public void testGetBSNForProject_NameIsNotDir()
			throws Exception
		{
			Project p = Workspace.getProject(new File("test/directoryNotBSN/proj1"));
			assertEquals("com.mycompany.package",p.getWorkspace().getBSNForProject(p));
		}
		
		public void testGetBSNForProject_NameIsNotDirMissingBSN()
			throws Exception
		{
			Project p = Workspace.getProject(new File("test/directoryNotBSN/proj2"));
			assertNull(p.getWorkspace().getBSNForProject(p));
		}
		
		public void testFindProject_BSNisDir()
				throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/gitWs/repo1"));
			Project p = ws.findProject("proj3");
			assertTrue("Was "+p.getBase().getAbsolutePath(),p.getBase().getAbsolutePath().endsWith("test/gitWs/repo2/proj3"));
		}
		
		
		public void testFindProject_BSNisNoDir()
				throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/directoryNotBSN"));
			Project p = ws.findProject("com.mycompany.package");
			assertTrue("Was "+p.getBase().getAbsolutePath(),p.getBase().getAbsolutePath().endsWith("test/directoryNotBSN/proj1"));
		}
		
		public void testFindProject_BSNisNoDir_NotFound()
				throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/directoryNotBSN"));
			Project p = ws.findProject("com.mycompany.another.package");
			assertNull(p);
		}
		
		public void testGetProjectFromLocation()
			throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/gitWs/repo1"));
			Project p = ws.getProjectFromLocation(new File("test/gitWs/repo1/proj1"));
			assertTrue("Was "+p.getBase().getAbsolutePath(),p.getBase().getAbsolutePath().endsWith("test/gitWs/repo1/proj1"));
			assertEquals(p,ws.getProjectFromLocation(new File("test/gitWs/repo1/proj1")));
			assertEquals(p,ws.getProject("proj1"));
		}
		
		public void testGetProject()
			throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/gitWs/repo1"));
			Project p = ws.getProject("proj1");
			assertTrue("Was "+p.getBase().getAbsolutePath(),p.getBase().getAbsolutePath().endsWith("test/gitWs/repo1/proj1"));
			assertEquals(p,ws.getProjectFromLocation(new File("test/gitWs/repo1/proj1")));
			assertEquals(p,ws.getProject("proj1"));
		}
		
		public void testAddProject_NotValid()
			throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/gitWs/repo1"));
			assertNull(ws.addProject(null));
			assertNull(ws.addProject(new Project(ws, new File("test/gitWs/repo2/bogus"))));
		}
		
		public void testAddProjectValid()
				throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/gitWs/repo1"));
			Project p = ws.addProject(new Project(ws, new File("test/gitWs/repo1/proj1")));
			assertNotNull(p);
			assertEquals(p,ws.getProjectFromLocation(new File("test/gitWs/repo1/proj1")));
			assertEquals(p,ws.getProject("proj1"));
		}
		
		public void testGetAllProjects()
			throws Exception
		{
			Workspace ws = Workspace.getWorkspace(new File("test/gitWs/repo1"));
			Collection<Project> col = ws.getAllProjects();
			assertEquals(3,col.size());
			Map<String,Project> map = new HashMap<String,Project>();
			for(Project p : col)
			{
				map.put(p.getName(), p);
			}
			assertTrue("Missing proj1",map.containsKey("proj1"));
			assertTrue("Missing proj2",map.containsKey("proj2"));
			assertTrue("Missing proj3",map.containsKey("proj3"));

		}

		
		@Override
		public void setUp()
		{
			Workspace.cache.clear();
		}

}
