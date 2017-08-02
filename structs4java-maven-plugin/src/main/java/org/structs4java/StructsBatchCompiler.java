package org.structs4java;

import static com.google.common.collect.Iterables.addAll;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.toArray;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;
import static java.util.Arrays.asList;
import static org.eclipse.xtext.util.Strings.concat;
import static org.eclipse.xtext.util.Strings.emptyIfNull;
import static org.eclipse.xtext.util.Strings.isEmpty;
import static org.eclipse.xtext.util.Strings.split;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.eclipse.xtend.core.compiler.batch.BootClassLoader;
import org.eclipse.xtend.core.macro.ProcessorInstanceForJvmTypeProvider.ProcessorClassloaderAdapter;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.common.types.access.impl.ClasspathTypeProvider;
import org.eclipse.xtext.common.types.access.impl.IndexedJvmTypeAccess;
import org.eclipse.xtext.common.types.descriptions.IStubGenerator;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.generator.GeneratorDelegate;
import org.eclipse.xtext.generator.JavaIoFileSystemAccess;
import org.eclipse.xtext.mwe.NameBasedFilter;
import org.eclipse.xtext.mwe.PathTraverser;
import org.eclipse.xtext.parser.IEncodingProvider;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.resource.CompilerPhases;
import org.eclipse.xtext.resource.FileExtensionProvider;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.resource.impl.ResourceSetBasedResourceDescriptions;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
//import org.eclipse.xtext.xbase.file.ProjectConfig;
//import org.eclipse.xtext.xbase.file.RuntimeWorkspaceConfigProvider;
//import org.eclipse.xtext.xbase.file.SimpleWorkspaceConfig;
//import org.eclipse.xtext.xbase.file.WorkspaceConfig;
import org.eclipse.xtext.xbase.resource.BatchLinkableResource;
import org.structs4java.structs4JavaDsl.StructsFile;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class StructsBatchCompiler {

	private final static class SeverityFilter implements Predicate<Issue> {
		private static final SeverityFilter WARNING = new SeverityFilter(Severity.WARNING);
		private static final SeverityFilter ERROR = new SeverityFilter(Severity.ERROR);
		private Severity severity;

		private SeverityFilter(Severity severity) {
			this.severity = severity;
		}

		public boolean apply(Issue issue) {
			return this.severity == issue.getSeverity();
		}
	}

	private static final FileFilter ACCEPT_ALL_FILTER = new FileFilter() {
		public boolean accept(File pathname) {
			return true;
		}
	};

	private XtextResourceSet resourceSet;
	@Inject
	private Provider<JavaIoFileSystemAccess> javaIoFileSystemAccessProvider;
	@Inject
	private FileExtensionProvider fileExtensionProvider;
	@Inject
	private Provider<ResourceSetBasedResourceDescriptions> resourceSetDescriptionsProvider;
	@Inject
	private GeneratorDelegate generator;
	@Inject
	private IndexedJvmTypeAccess indexedJvmTypeAccess;
	// @Inject
	// private ProcessorInstanceForJvmTypeProvider annotationProcessorFactory;
	@Inject
	private IEncodingProvider.Runtime encodingProvider;
	@Inject
	private IResourceDescription.Manager resourceDescriptionManager;
	// @Inject
	// private RuntimeWorkspaceConfigProvider workspaceConfigProvider;
	@Inject
	private CompilerPhases compilerPhases;
	@Inject
	private IStubGenerator stubGenerator;

	private Writer outputWriter;
	private Writer errorWriter;
	private String sourcePath;
	private String classPath;
	private String structSourceRoot;
	/**
	 * @since 2.7
	 */
	private String bootClassPath;
	private boolean useCurrentClassLoaderAsParent;
	private String outputPath;
	private String fileEncoding;
	private String complianceLevel = "1.5";
	private boolean verbose = false;
	private String tempDirectory = System.getProperty("java.io.tmpdir");
	private boolean deleteTempDirectory = true;
	private List<File> tempFolders = Lists.newArrayList();
	private boolean writeTraceFiles = true;
	private ClassLoader currentClassLoader = getClass().getClassLoader();

	public void setCurrentClassLoader(ClassLoader currentClassLoader) {
		this.currentClassLoader = currentClassLoader;
	}

	public void setUseCurrentClassLoaderAsParent(boolean useCurrentClassLoaderAsParent) {
		this.useCurrentClassLoaderAsParent = useCurrentClassLoaderAsParent;
	}

	public String getTempDirectory() {
		return tempDirectory;
	}

	public void setTempDirectory(String tempDirectory) {
		this.tempDirectory = tempDirectory;
	}

	public boolean isWriteTraceFiles() {
		return writeTraceFiles;
	}

	public void setWriteTraceFiles(boolean writeTraceFiles) {
		this.writeTraceFiles = writeTraceFiles;
	}

	public void setResourceSet(XtextResourceSet resourceSet) {
		this.resourceSet = resourceSet;
	}

	public boolean isDeleteTempDirectory() {
		return deleteTempDirectory;
	}

	public void setDeleteTempDirectory(boolean deletetempDirectory) {
		this.deleteTempDirectory = deletetempDirectory;
	}

	public Writer getOutputWriter() {
		if (outputWriter == null) {
			outputWriter = new Writer() {
				@Override
				public void write(char[] data, int offset, int count) throws IOException {
					String message = String.copyValueOf(data, offset, count);
					if (!Strings.isEmpty(message.trim())) {
						System.out.println(message);
					}
				}

				@Override
				public void flush() throws IOException {
				}

				@Override
				public void close() throws IOException {
				}
			};
		}
		return outputWriter;
	}

	public void setOutputWriter(Writer ouputWriter) {
		this.outputWriter = ouputWriter;
	}

	public Writer getErrorWriter() {
		if (errorWriter == null) {
			errorWriter = new Writer() {
				@Override
				public void write(char[] data, int offset, int count) throws IOException {
					String message = String.copyValueOf(data, offset, count);
					if (!Strings.isEmpty(message.trim())) {
						System.out.println(message);
					}
				}

				@Override
				public void flush() throws IOException {
				}

				@Override
				public void close() throws IOException {
				}
			};
		}
		return errorWriter;
	}

	public void setErrorWriter(Writer errorWriter) {
		this.errorWriter = errorWriter;
	}

	public void setClassPath(String classPath) {
		this.classPath = classPath;
	}

	/**
	 * @since 2.7
	 */
	public void setBootClassPath(String bootClassPath) {
		this.bootClassPath = bootClassPath;
	}

	public void setOutputPath(String outputPath) {
		this.outputPath = outputPath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public void setStructSourceRoot(String structSourceRoot) {
		this.structSourceRoot = structSourceRoot;
	}

	protected String getComplianceLevel() {
		return complianceLevel;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	protected boolean isVerbose() {
		return verbose;
	}

	public String getFileEncoding() {
		return fileEncoding;
	}

	public void setFileEncoding(String encoding) {
		this.fileEncoding = encoding;
	}

	//private boolean configureWorkspace() {
		// List<File> sourceFileList = getSourcePathFileList();
		// File outputFile = getOutputPathFile();
		// if (sourceFileList == null || outputFile == null) {
		// return false;
		// }
		//
		// File commonRoot = determineCommonRoot(outputFile, sourceFileList);
		//
		// // We don't want to use root ("/") as a workspace folder, didn't we?
		// if (commonRoot == null || commonRoot.getParent() == null ||
		// commonRoot.getParentFile().getParent() == null) {
		// log.error("All source folders and the output folder should have "
		// + "a common parent non-top level folder (like project folder)");
		// for (File sourceFile : sourceFileList) {
		// log.error("(Source folder: '" + sourceFile + "')");
		// }
		// log.error("(Output folder: '" + outputFile + "')");
		// return false;
		// }

		// SimpleWorkspaceConfig workspaceConfig = new
		// SimpleWorkspaceConfig(commonRoot.getParent().toString());
		// ProjectConfig projectConfig = new
		// ProjectConfig(commonRoot.getName());
		//
		// java.net.URI commonURI = commonRoot.toURI();
		// java.net.URI relativizedTarget =
		// commonURI.relativize(outputFile.toURI());
		// if (relativizedTarget.isAbsolute()) {
		// log.error("Target folder '" + outputFile + "' must be a child of the
		// project folder '" + commonRoot + "'");
		// return false;
		// }
		//
		// for (File source : sourceFileList) {
		// java.net.URI relativizedSrc = commonURI.relativize(source.toURI());
		// if (relativizedSrc.isAbsolute()) {
		// log.error("Source folder '" + source + "' must be a child of the
		// project folder '" + commonRoot + "'");
		// return false;
		// }
		// projectConfig.addSourceFolderMapping(relativizedSrc.getPath(),
		// relativizedTarget.getPath());
		// }
		// workspaceConfig.addProjectConfig(projectConfig);
		// workspaceConfigProvider.setWorkspaceConfig(workspaceConfig);
	//	return true;
	//}

//	private File getOutputPathFile() {
//		try {
//			return new File(outputPath).getCanonicalFile();
//		} catch (IOException e) {
//			System.out.println("Invalid target folder '" + outputPath + "' (" + e.getMessage() + ")");
//			return null;
//		}
//	}
//
//	private List<File> getSourcePathFileList() {
//		List<File> sourceFileList = new ArrayList<File>();
//		for (String path : getSourcePathDirectories()) {
//			try {
//				sourceFileList.add(new File(path).getCanonicalFile());
//			} catch (IOException e) {
//				System.out.println("Invalid source folder '" + path + "' (" + e.getMessage() + ")");
//				return null;
//			}
//		}
//		return sourceFileList;
//	}
//
//	private File determineCommonRoot(File outputFile, List<File> sourceFileList) {
//		List<File> pathList = new ArrayList<File>(sourceFileList);
//		pathList.add(outputFile);
//
//		List<List<File>> pathParts = new ArrayList<List<File>>();
//
//		for (File path : pathList) {
//			List<File> partsList = new ArrayList<File>();
//			File subdir = path;
//			while (subdir != null) {
//				partsList.add(subdir);
//				subdir = subdir.getParentFile();
//			}
//			pathParts.add(partsList);
//		}
//		int i = 1;
//		File result = null;
//		while (true) {
//			File compareWith = null;
//			for (List<File> parts : pathParts) {
//				if (parts.size() < i) {
//					return result;
//				}
//				File part = parts.get(parts.size() - i);
//				if (compareWith == null) {
//					compareWith = part;
//				} else {
//					if (!compareWith.equals(part)) {
//						return result;
//					}
//				}
//			}
//			result = compareWith;
//			i++;
//		}
//	}

	public boolean compile() {
		try {
			// if (workspaceConfigProvider.getWorkspaceConfig() == null) {
			// if (!configureWorkspace()) {
			// return false;
			// }
			// }
			resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);

			File classDirectory = createTempDir("classes");
			try {
				compilerPhases.setIndexing(resourceSet, true);
				// install a type provider without index lookup for the first
				// phase
				installJvmTypeProvider(resourceSet, classDirectory, true);
				loadStructsFiles(resourceSet);
				File sourceDirectory = createStubs(resourceSet);
				if (!preCompileStubs(sourceDirectory, classDirectory)) {
					System.out.println(
							"Compilation of stubs and existing Java code had errors. This is expected and usually is not a problem.");
				}
			} finally {
				compilerPhases.setIndexing(resourceSet, false);
			}
			// install a fresh type provider for the second phase, so we clear
			// all previously cached classes and misses.
			installJvmTypeProvider(resourceSet, classDirectory, false);
			EcoreUtil.resolveAll(resourceSet);
			List<Issue> issues = validate(resourceSet);
			Iterable<Issue> errors = Iterables.filter(issues, SeverityFilter.ERROR);
			Iterable<Issue> warnings = Iterables.filter(issues, SeverityFilter.WARNING);
			reportIssues(Iterables.concat(errors, warnings));
			if (!Iterables.isEmpty(errors)) {
				return false;
			}
			generateJavaFiles(resourceSet);
		} finally {
			if (isDeleteTempDirectory()) {
				deleteTmpFolders();
			}
		}
		return true;
	}

	protected ResourceSet loadStructsFiles(final ResourceSet resourceSet) {
		encodingProvider.setDefaultEncoding(getFileEncoding());
		final NameBasedFilter nameBasedFilter = new NameBasedFilter();
		nameBasedFilter.setExtension(fileExtensionProvider.getPrimaryFileExtension());
		PathTraverser pathTraverser = new PathTraverser();
		List<String> sourcePathDirectories = getStructsSourcePathDirectories();
		Multimap<String, URI> pathes = pathTraverser.resolvePathes(sourcePathDirectories, new Predicate<URI>() {
			public boolean apply(URI input) {
				boolean matches = nameBasedFilter.matches(input);
				return matches;
			}
		});
		for (String src : pathes.keySet()) {
			URI baseDir = URI.createFileURI(src + "/");
			String identifier = Joiner.on("_").join(baseDir.segments());
			URI platformResourceURI = URI.createPlatformResourceURI(identifier + "/", true);
			resourceSet.getURIConverter().getURIMap().put(platformResourceURI, baseDir);
			for (URI uri : pathes.get(src)) {
				URI uriToUse = uri.replacePrefix(baseDir, platformResourceURI);
				System.out.println("load structs file '" + uriToUse + "'");
				resourceSet.getResource(uriToUse, true);
			}
		}
		return resourceSet;
	}

	protected File createStubs(ResourceSet resourceSet) {
		File outputDirectory = createTempDir("stubs");
		JavaIoFileSystemAccess fileSystemAccess = javaIoFileSystemAccessProvider.get();
		fileSystemAccess.setOutputPath(outputDirectory.toString());
		List<Resource> resources = Lists.newArrayList(resourceSet.getResources());
		for (Resource resource : resources) {
			IResourceDescription description = resourceDescriptionManager.getResourceDescription(resource);
			stubGenerator.doGenerateStubs(fileSystemAccess, description);
		}
		return outputDirectory;
	}

	protected boolean preCompileStubs(File tmpSourceDirectory, File classDirectory) {
		List<String> commandLine = Lists.newArrayList();
		// todo args
		if (isVerbose()) {
			commandLine.add("-verbose");
		}
		if (!isEmpty(bootClassPath)) {
			commandLine.add("-bootclasspath \"" + concat(File.pathSeparator, getBootClassPathEntries()) + "\"");
		}
		if (!isEmpty(classPath)) {
			commandLine.add("-cp \"" + concat(File.pathSeparator, getClassPathEntries()) + "\"");
		}
		commandLine.add("-d \"" + classDirectory.toString() + "\"");
		commandLine.add("-" + getComplianceLevel());
		commandLine.add("-proceedOnError");
		List<String> sourceDirectories = newArrayList(getSourcePathDirectories());
		sourceDirectories.add(tmpSourceDirectory.toString());
		commandLine.add(concat(" ", transform(sourceDirectories, new Function<String, String>() {

			public String apply(String path) {
				return "\"" + path + "\"";
			}
		})));
		System.out.println("invoke batch compiler with '" + concat(" ", commandLine) + "'");
		return BatchCompiler.compile(concat(" ", commandLine), new PrintWriter(getOutputWriter()),
				new PrintWriter(getErrorWriter()), null);
	}

	protected List<Issue> validate(ResourceSet resourceSet) {
		List<Issue> issues = Lists.newArrayList();
		List<Resource> resources = Lists.newArrayList(resourceSet.getResources());
		for (Resource resource : resources) {
			IResourceServiceProvider resourceServiceProvider = IResourceServiceProvider.Registry.INSTANCE
					.getResourceServiceProvider(resource.getURI());
			if (resourceServiceProvider != null) {
				IResourceValidator resourceValidator = resourceServiceProvider.getResourceValidator();
				List<Issue> result = resourceValidator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl);
				addAll(issues, result);
			}
		}
		return issues;
	}

	/**
	 * Installs the complete JvmTypeProvider including index access into the
	 * {@link ResourceSet}. The lookup classpath is enhanced with the given tmp
	 * directory.
	 * 
	 * @deprecated use the explicit variant
	 *             {@link #installJvmTypeProvider(ResourceSet, File, boolean)}
	 *             instead.
	 */
	@Deprecated
	protected void installJvmTypeProvider(XtextResourceSet resourceSet, File tmpClassDirectory) {
		internalInstallJvmTypeProvider(resourceSet, tmpClassDirectory, false);
	}

	/**
	 * Installs the JvmTypeProvider optionally including index access into the
	 * {@link ResourceSet}. The lookup classpath is enhanced with the given tmp
	 * directory.
	 */
	protected void installJvmTypeProvider(XtextResourceSet resourceSet, File tmpClassDirectory,
			boolean skipIndexLookup) {
		if (skipIndexLookup) {
			internalInstallJvmTypeProvider(resourceSet, tmpClassDirectory, skipIndexLookup);
		} else {
			// delegate to the deprecated signature in case it was overridden by
			// clients
			installJvmTypeProvider(resourceSet, tmpClassDirectory);
		}
	}

	/**
	 * Performs the actual installation of the JvmTypeProvider.
	 */
	private void internalInstallJvmTypeProvider(XtextResourceSet resourceSet, File tmpClassDirectory,
			boolean skipIndexLookup) {
		Iterable<String> classPathEntries = concat(getClassPathEntries(), getSourcePathDirectories(),
				asList(tmpClassDirectory.toString()));
		classPathEntries = filter(classPathEntries, new Predicate<String>() {
			public boolean apply(String input) {
				return !Strings.isEmpty(input.trim());
			}
		});
		Function<String, URL> toUrl = new Function<String, URL>() {
			public URL apply(String from) {
				try {
					return new File(from).toURI().toURL();
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}
			}
		};
		Iterable<URL> classPathUrls = Iterables.transform(classPathEntries, toUrl);
		System.out.println("classpath used for Struct compilation : " + classPathUrls);
		ClassLoader parentClassLoader;
		if (useCurrentClassLoaderAsParent) {
			parentClassLoader = currentClassLoader;
		} else {
			if (isEmpty(bootClassPath)) {
				parentClassLoader = ClassLoader.getSystemClassLoader().getParent();
			} else {
				Iterable<URL> bootClassPathUrls = Iterables.transform(getBootClassPathEntries(), toUrl);
				parentClassLoader = new BootClassLoader(toArray(bootClassPathUrls, URL.class));
			}
		}
		ClassLoader urlClassLoader = new URLClassLoader(toArray(classPathUrls, URL.class), parentClassLoader);
		new ClasspathTypeProvider(urlClassLoader, resourceSet, skipIndexLookup ? null : indexedJvmTypeAccess);
		resourceSet.setClasspathURIContext(urlClassLoader);

		// for annotation processing we need to have the compiler's classpath as
		// a parent.
		URLClassLoader urlClassLoaderForAnnotationProcessing = new URLClassLoader(toArray(classPathUrls, URL.class),
				currentClassLoader);

		resourceSet.eAdapters().add(new ProcessorClassloaderAdapter(urlClassLoaderForAnnotationProcessing));
		// annotationProcessorFactory.setClassLoader(urlClassLoaderForAnnotationProcessing);
		// System.out.println("annotationProcessorFactory:
		// "+annotationProcessorFactory);
	}

	protected void reportIssues(Iterable<Issue> issues) {
		for (Issue issue : issues) {
			StringBuilder issueBuilder = createIssueMessage(issue);
			if (Severity.ERROR == issue.getSeverity()) {
				System.out.println(issueBuilder.toString());
			} else if (Severity.WARNING == issue.getSeverity()) {
				System.out.println(issueBuilder.toString());
			}
		}
	}

	private StringBuilder createIssueMessage(Issue issue) {
		StringBuilder issueBuilder = new StringBuilder("\n");
		issueBuilder.append(issue.getSeverity()).append(": \t");
		URI uriToProblem = issue.getUriToProblem();
		if (uriToProblem != null) {
			URI resourceUri = uriToProblem.trimFragment();
			issueBuilder.append(resourceUri.lastSegment()).append(" - ");
			if (resourceUri.isFile()) {
				issueBuilder.append(resourceUri.toFileString());
			}
		}
		issueBuilder.append("\n").append(issue.getLineNumber()).append(": ").append(issue.getMessage());
		return issueBuilder;
	}

	protected void generateJavaFiles(ResourceSet resourceSet) {
		JavaIoFileSystemAccess javaIoFileSystemAccess = javaIoFileSystemAccessProvider.get();
		javaIoFileSystemAccess.setOutputPath(outputPath);
		javaIoFileSystemAccess.setWriteTrace(writeTraceFiles);

		System.out.println("generateJavaFiles: " + outputPath);
		for (Resource resource : newArrayList(resourceSet.getResources())) {
			if (resource.getContents().get(0) instanceof StructsFile) {
				System.out.println("Generating source for: " + resource);
				generator.doGenerate(resource, javaIoFileSystemAccess);
			}
		}
	}

	protected ResourceSetBasedResourceDescriptions getResourceDescriptions(ResourceSet resourceSet) {
		ResourceSetBasedResourceDescriptions resourceDescriptions = resourceSetDescriptionsProvider.get();
		resourceDescriptions.setContext(resourceSet);
		resourceDescriptions.setRegistry(IResourceServiceProvider.Registry.INSTANCE);
		return resourceDescriptions;
	}

	/* @Nullable */protected StructsFile getStructsFile(Resource resource) {
		XtextResource xtextResource = (XtextResource) resource;
		IParseResult parseResult = xtextResource.getParseResult();
		if (parseResult != null) {
			EObject model = parseResult.getRootASTElement();
			if (model instanceof StructsFile) {
				StructsFile structsFile = (StructsFile) model;
				return structsFile;
			}
		}
		return null;
	}

	protected List<String> getClassPathEntries() {
		return getDirectories(classPath);
	}

	/**
	 * @since 2.7
	 */
	protected List<String> getBootClassPathEntries() {
		return getDirectories(bootClassPath);
	}

	protected List<String> getSourcePathDirectories() {
		return getDirectories(sourcePath);
	}

	protected List<String> getStructsSourcePathDirectories() {
		return getDirectories(structSourceRoot);
	}

	protected List<String> getDirectories(String path) {
		if (Strings.isEmpty(path)) {
			return Lists.newArrayList();
		}
		final List<String> split = split(emptyIfNull(path), File.pathSeparator);
		return transform(split, new Function<String, String>() {
			public String apply(String from) {
				return new File(new File(from).getAbsoluteFile().toURI().normalize()).getAbsolutePath();
			}
		});
	}

	protected File createTempDir(String prefix) {
		File tempDir = new File(getTempDirectory(), prefix + System.nanoTime());
		cleanFolder(tempDir, ACCEPT_ALL_FILTER, true, true);
		if (!tempDir.mkdirs()) {
			throw new RuntimeException("Error creating temp directory '" + tempDir.getAbsolutePath() + "'");
		}
		tempFolders.add(tempDir);
		return tempDir;
	}

	protected void deleteTmpFolders() {
		for (File file : tempFolders) {
			cleanFolder(file, ACCEPT_ALL_FILTER, true, true);
		}
	}

	// FIXME: use Files#cleanFolder after the maven distro availability of
	// version 2.2.x
	protected static boolean cleanFolder(File parentFolder, FileFilter filter, boolean continueOnError,
			boolean deleteParentFolder) {
		if (!parentFolder.exists()) {
			return true;
		}
		if (filter == null)
			filter = ACCEPT_ALL_FILTER;
		System.out.println("Cleaning folder " + parentFolder.toString());
		final File[] contents = parentFolder.listFiles(filter);
		for (int j = 0; j < contents.length; j++) {
			final File file = contents[j];
			if (file.isDirectory()) {
				if (!cleanFolder(file, filter, continueOnError, true) && !continueOnError)
					return false;
			} else {
				if (!file.delete()) {
					System.out.println("Couldn't delete " + file.getAbsolutePath());
					if (!continueOnError)
						return false;
				}
			}
		}
		if (deleteParentFolder) {
			if (parentFolder.list().length == 0 && !parentFolder.delete()) {
				System.out.println("Couldn't delete " + parentFolder.getAbsolutePath());
				return false;
			}
		}
		return true;
	}

}