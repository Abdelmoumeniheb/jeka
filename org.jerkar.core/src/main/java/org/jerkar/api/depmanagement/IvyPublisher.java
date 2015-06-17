package org.jerkar.api.depmanagement;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.cache.ResolutionCacheManager;
import org.apache.ivy.core.deliver.DeliverOptions;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.MDArtifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorWriter;
import org.apache.ivy.plugins.parser.m2.PomWriterOptions;
import org.apache.ivy.plugins.resolver.AbstractPatternsBasedResolver;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.jerkar.api.crypto.pgp.JkPgp;
import org.jerkar.api.depmanagement.JkPublishRepos.JkPublishRepo;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.utils.JkUtilsFile;
import org.jerkar.api.utils.JkUtilsString;
import org.jerkar.api.utils.JkUtilsThrowable;
import org.jerkar.api.utils.JkUtilsTime;

/**
 * Jerkar users : This class is not part of the public API !!! Please, Use {@link JkPublisher} instead.
 * Ivy wrapper providing high level methods. The API is expressed using Jerkar classes only (mostly free of Ivy classes).
 */
final class IvyPublisher implements InternalPublisher {

	private final Ivy ivy;

	private final JkPublishRepos publishRepos;

	private final File descriptorOutputDir;

	private IvyPublisher(Ivy ivy, JkPublishRepos publishRepo, File descriptorOutputDir) {
		super();
		this.ivy = ivy;
		this.publishRepos = publishRepo;
		this.descriptorOutputDir = descriptorOutputDir;
		ivy.getLoggerEngine().setDefaultLogger(new MessageLogger());
	}

	private static IvyPublisher of(IvySettings ivySettings, JkPublishRepos publishRepos, File descriptorOutputDir) {
		final Ivy ivy = Ivy.newInstance(ivySettings);
		return new IvyPublisher(ivy, publishRepos, descriptorOutputDir);
	}

	/**
	 * Creates an <code>IvySettings</code> from the specified repositories.
	 */
	private static IvySettings ivySettingsOf(JkPublishRepos publishRepos) {
		final IvySettings ivySettings = new IvySettings();
		Translations.populateIvySettingsWithPublishRepo(ivySettings, publishRepos);
		return ivySettings;
	}

	/**
	 * Creates an instance using specified repository for publishing and
	 * the specified repositories for resolving.
	 */
	public static IvyPublisher of(JkPublishRepos publishRepos, File descriptorOutputDir) {
		return of(ivySettingsOf(publishRepos), publishRepos, descriptorOutputDir);
	}



	private static boolean isMaven(DependencyResolver dependencyResolver) {
		if (dependencyResolver instanceof ChainResolver) {
			final ChainResolver resolver = (ChainResolver) dependencyResolver;
			@SuppressWarnings("rawtypes")
			final List list = resolver.getResolvers();
			if (list.isEmpty()) {
				return false;
			}
			return isMaven((DependencyResolver) list.get(0));
		}
		if (dependencyResolver instanceof AbstractPatternsBasedResolver) {
			final AbstractPatternsBasedResolver resolver = (AbstractPatternsBasedResolver) dependencyResolver;
			return resolver.isM2compatible();
		}
		throw new IllegalStateException(dependencyResolver.getClass().getName() + " not handled");
	}

	@Override
	public boolean hasMavenPublishRepo() {
		for (final DependencyResolver dependencyResolver : Translations.publishResolverOf(this.ivy.getSettings())) {
			if (isMaven(dependencyResolver)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean hasIvyPublishRepo() {
		for (final DependencyResolver dependencyResolver : Translations.publishResolverOf(this.ivy.getSettings())) {
			if (!isMaven(dependencyResolver)) {
				return true;
			}
		}
		return false;
	}


	/**
	 * Publish the specified module. Dependencies, default scopes and mapping are necessary
	 * in order to generate the ivy.xml file.
	 * 
	 * @param versionedModule The module/version to publish.
	 * @param publication The artifacts to publish.
	 * @param dependencies The dependencies of the published module.
	 * @param defaultScope The default scope of the published module
	 * @param defaultMapping The default scope mapping of the published module
	 * @param deliveryDate The delivery date.
	 */
	@Override
	public void publishIvy(JkVersionedModule versionedModule, JkIvyPublication publication, JkDependencies dependencies, JkScope defaultScope, JkScopeMapping defaultMapping, Date deliveryDate) {
		JkLog.startln("Publishing for Ivy");
		final ModuleDescriptor moduleDescriptor = createModuleDescriptor(versionedModule, publication, dependencies, defaultScope, defaultMapping, deliveryDate);
		publishIvyArtifacts(publication, deliveryDate, moduleDescriptor);
		JkLog.done();
	}


	@Override
	public void publishMaven(JkVersionedModule versionedModule, JkMavenPublication publication, JkDependencies dependencies, Date deliveryDate) {
		JkLog.startln("Publishing for Maven");
		final JkDependencies publishedDependencies = resolveDependencies(versionedModule, dependencies);
		final DefaultModuleDescriptor moduleDescriptor = createModuleDescriptor(versionedModule, publication,
				publishedDependencies,deliveryDate);

		publishMavenArtifacts(publication, deliveryDate, moduleDescriptor);
		JkLog.done();
	}

	@SuppressWarnings("unchecked")
	private JkDependencies resolveDependencies(JkVersionedModule module, JkDependencies dependencies) {
		if (!dependencies.hasDynamicAndResovableVersions()) {
			return dependencies;
		}
		final ModuleRevisionId moduleRevisionId = Translations.toModuleRevisionId(module);
		final ResolutionCacheManager cacheManager = this.ivy.getSettings().getResolutionCacheManager();
		final File cachedIvyFile = cacheManager.getResolvedIvyFileInCache(moduleRevisionId);
		final File cachedPropFile = cacheManager.getResolvedIvyPropertiesInCache(moduleRevisionId);
		if (!cachedIvyFile.exists() || !cachedPropFile.exists()) {
			JkLog.start("Cached resolved ivy file not found for " + module + ". Performing a fresh resolve");
			final ModuleDescriptor moduleDescriptor = Translations.toPublicationFreeModule(module, dependencies, null, null);
			final ResolveOptions resolveOptions = new ResolveOptions();
			resolveOptions.setConfs(new String[] {"*"});
			resolveOptions.setTransitive(false);
			resolveOptions.setOutputReport(JkLog.verbose());
			resolveOptions.setLog(logLevel());
			resolveOptions.setRefresh(true);
			final ResolveReport report;
			try {
				report = ivy.resolve(moduleDescriptor, resolveOptions);
			} catch (final Exception e1) {
				throw new IllegalStateException(e1);
			} finally {
				JkLog.done();
			}
			if (report.hasError()) {
				JkLog.error(report.getAllProblemMessages());
				cachedIvyFile.delete();
				throw new IllegalStateException("Error while reloving dependencies : "
						+ JkUtilsString.join(report.getAllProblemMessages(), ", "));
			}
		}
		try {
			cacheManager.getResolvedModuleDescriptor(moduleRevisionId);
		} catch (final ParseException e) {
			throw new IllegalStateException(e);
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
		final Properties props = JkUtilsFile.readPropertyFile(cachedPropFile);
		final Map<JkModuleId, JkVersion> resolvedVersions = Translations.toModuleVersionMap(props);
		return dependencies.resolvedWith(resolvedVersions);
	}

	private int publishIvyArtifacts(JkIvyPublication publication, Date date, ModuleDescriptor moduleDescriptor) {
		int count = 0;
		for (final DependencyResolver resolver : Translations.publishResolverOf(this.ivy.getSettings())) {
			final JkPublishRepo publishRepo = this.publishRepos.getRepoHavingUrl(Translations.publishResolverUrl(resolver));
			final JkVersionedModule jkModule = Translations.toJerkarVersionedModule(moduleDescriptor.getModuleRevisionId());
			if (!isMaven(resolver) && publishRepo.filter().accept(jkModule)) {
				JkLog.startln("Publishing for repository " + resolver);
				this.publishIvyArtifacts(resolver, publication, date, moduleDescriptor);
				JkLog.done();;
				count++;
			}
		}
		return count;
	}

	private void publishIvyArtifacts(DependencyResolver resolver, JkIvyPublication publication, Date date, ModuleDescriptor moduleDescriptor) {
		final ModuleRevisionId ivyModuleRevisionId = moduleDescriptor.getModuleRevisionId();
		try {
			resolver.beginPublishTransaction(ivyModuleRevisionId, true);
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
		File publishedIvy;
		try {
			for (final JkIvyPublication.Artifact artifact : publication) {
				final Artifact ivyArtifact = Translations.toPublishedArtifact(artifact, ivyModuleRevisionId, date);
				try {
					resolver.publish(ivyArtifact, artifact.file, true);
				} catch (final IOException e) {
					throw new IllegalStateException(e);
				}
			}

			// Publish Ivy file
			publishedIvy = this.ivy.getSettings().resolveFile(IvyPatternHelper.substitute(ivyPatternForIvyFiles(),
					moduleDescriptor.getResolvedModuleRevisionId()));
			final Artifact artifact = MDArtifact.newIvyArtifact(moduleDescriptor);
			resolver.publish(artifact, publishedIvy, true);
		} catch (final Exception e) {
			abortPublishTransaction(resolver);
			throw JkUtilsThrowable.unchecked(e);
		}
		try {
			commitPublication(resolver);
		} finally {
			publishedIvy.delete();
		}
	}

	private int publishMavenArtifacts(JkMavenPublication publication, Date date, DefaultModuleDescriptor moduleDescriptor) {
		int count = 0;
		for (final DependencyResolver resolver : Translations.publishResolverOf(this.ivy.getSettings())) {
			final JkPublishRepo publishRepo = this.publishRepos.getRepoHavingUrl(Translations.publishResolverUrl(resolver));
			final JkVersionedModule jkModule = Translations.toJerkarVersionedModule(moduleDescriptor.getModuleRevisionId());
			if (isMaven(resolver) && publishRepo.filter().accept(jkModule)) {
				JkLog.startln("Publishing for repository " + resolver);
				this.publishMavenArtifacts(resolver, publication, date, moduleDescriptor, CheckFileFlag.of(publishRepo),
						publishRepo.snapshotTimestampPattern());
				JkLog.done();
				count ++;
			}
		}
		return count;
	}

	private void publishMavenArtifacts(DependencyResolver resolver, JkMavenPublication publication, Date date, DefaultModuleDescriptor moduleDescriptor, CheckFileFlag checkProducer, String timestampPattern) {
		ModuleRevisionId ivyModuleRevisionId = moduleDescriptor.getModuleRevisionId();
		ivyModuleRevisionId = withPattern(ivyModuleRevisionId, timestampPattern, JkUtilsTime.now());
		try {
			resolver.beginPublishTransaction(ivyModuleRevisionId, true);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		try {
			final File pomXml = new File(targetDir(), "pom.xml");
			final String packaging = JkUtilsString.substringAfterLast(publication.mainArtifactFile().getName(),".");
			final Artifact artifact = new DefaultArtifact(ivyModuleRevisionId, date, publication.artifactName(), "pom", "pom", true);
			final PomWriterOptions pomWriterOptions = new PomWriterOptions();
			pomWriterOptions.setArtifactPackaging(packaging);
			File fileToDelete = null;
			if (publication.extraInfo() != null) {
				final File template = PomTemplateGenerator.generateTemplate(publication.extraInfo());
				pomWriterOptions.setTemplate(template);
				fileToDelete = template;
			}
			JkLog.info("Creating " + pomXml.getAbsolutePath());
			PomModuleDescriptorWriter.write(moduleDescriptor, pomXml, pomWriterOptions);

			if (fileToDelete != null) {
				JkUtilsFile.delete(fileToDelete);
			}
			resolver.publish(artifact, pomXml, true);
			checkProducer.publishChecks(resolver, artifact, pomXml);

			final Artifact mavenMainArtifact = Translations.toPublishedMavenArtifact(publication.mainArtifactFile(), publication.artifactName(),
					null, ivyModuleRevisionId, date);
			resolver.publish(mavenMainArtifact, publication.mainArtifactFile(), true);
			checkProducer.publishChecks(resolver, mavenMainArtifact, publication.mainArtifactFile());

			for (final Map.Entry<String, File> extraArtifact : publication.extraArtifacts().entrySet()) {
				final String classifier = extraArtifact.getKey();
				final File file = extraArtifact.getValue();
				final Artifact mavenArtifact = Translations.toPublishedMavenArtifact(file, publication.artifactName(),
						classifier, ivyModuleRevisionId, date);
				resolver.publish(mavenArtifact, file, true);
				checkProducer.publishChecks(resolver, mavenArtifact, file);
			}


		} catch (final Exception e) {
			abortPublishTransaction(resolver);
			throw JkUtilsThrowable.unchecked(e);
		}
		commitPublication(resolver);
	}

	private static ModuleRevisionId withPattern(ModuleRevisionId original, String pattern, Date time) {
		if (pattern == null) {
			return original;
		}
		if (original.getRevision().contains("-SNAPSHOT")) {
			final String newRev = original.getRevision().replace("-SNAPSHOT", "-" + new SimpleDateFormat(pattern).format(time));
			return ModuleRevisionId.newInstance(original, newRev);
		}
		return original;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Artifact withExtension(Artifact ar, String ext) {
		return new DefaultArtifact(ar.getModuleRevisionId(), ar.getPublicationDate(),
				ar.getName(), ar.getType(), ext, ar.getUrl(), new HashMap(ar.getExtraAttributes()));
	}

	private static void abortPublishTransaction(DependencyResolver resolver) {
		try {
			resolver.abortPublishTransaction();
		} catch (final IOException e) {
			JkLog.warn("Publish transction hasn't been properly aborted");
			e.printStackTrace(JkLog.warnStream());
		}
	}

	private ModuleDescriptor createModuleDescriptor(JkVersionedModule jkVersionedModule, JkIvyPublication publication, JkDependencies dependencies, JkScope defaultScope, JkScopeMapping defaultMapping, Date deliveryDate) {
		final ModuleRevisionId moduleRevisionId = Translations.toModuleRevisionId(jkVersionedModule);

		// First : update the module ivy cache.
		final ResolutionCacheManager cacheManager = this.ivy.getSettings().getResolutionCacheManager();
		final File cachedIvyFile = cacheManager.getResolvedIvyFileInCache(moduleRevisionId);
		final File propsFile = cacheManager.getResolvedIvyPropertiesInCache(moduleRevisionId);
		final DefaultModuleDescriptor moduleDescriptor = Translations.toPublicationFreeModule(jkVersionedModule, dependencies, defaultScope, defaultMapping);
		Translations.populateModuleDescriptorWithPublication(moduleDescriptor, publication, deliveryDate);
		try {
			cacheManager.saveResolvedModuleDescriptor(moduleDescriptor);
		} catch (final Exception e) {
			cachedIvyFile.delete();
			propsFile.delete();
			throw new RuntimeException("Error while creating cache file for " + moduleRevisionId + ". Deleting potentially corrupted cache files.", e);
		}

		// Second : update the module property cache (by invoking resolution)
		this.resolveDependencies(jkVersionedModule, dependencies);

		// Third : invoke the deliver process in order to generate the module ivy file.
		final DeliverOptions deliverOptions = new DeliverOptions();
		if (publication.status != null) {
			deliverOptions.setStatus(publication.status.name());
		}
		deliverOptions.setPubBranch(publication.branch);
		deliverOptions.setPubdate(deliveryDate);
		try {
			this.ivy.getDeliverEngine().deliver(moduleRevisionId, moduleRevisionId.getRevision(), ivyPatternForIvyFiles(), deliverOptions);
		} catch (final Exception e) {
			throw JkUtilsThrowable.unchecked(e);
		}

		return moduleDescriptor;
	}

	private DefaultModuleDescriptor createModuleDescriptor(JkVersionedModule jkVersionedModule, JkMavenPublication publication, JkDependencies resolvedDependencies, Date deliveryDate) {
		final ModuleRevisionId moduleRevisionId = Translations.toModuleRevisionId(jkVersionedModule);
		final ResolutionCacheManager cacheManager = this.ivy.getSettings().getResolutionCacheManager();
		final File cachedIvyFile = cacheManager.getResolvedIvyFileInCache(moduleRevisionId);
		final File propsFile = cacheManager.getResolvedIvyPropertiesInCache(moduleRevisionId);

		final DefaultModuleDescriptor moduleDescriptor = Translations.toPublicationFreeModule(jkVersionedModule, resolvedDependencies, null, null);
		Translations.populateModuleDescriptorWithPublication(moduleDescriptor, publication, deliveryDate);

		try {
			cacheManager.saveResolvedModuleDescriptor(moduleDescriptor);
		} catch (final Exception e) {
			cachedIvyFile.delete();
			propsFile.delete();
			throw new RuntimeException("Error while creating cache file for "
					+ moduleRevisionId + ". Deleting potentially corrupted cache files.", e);
		}
		return moduleDescriptor;
	}

	private String ivyPatternForIvyFiles() {
		return targetDir() + "/jerkar/[organisation]-[module]-[revision]-ivy.xml";
	}

	private String targetDir() {
		return this.descriptorOutputDir.getAbsolutePath();
	}

	private static String logLevel() {
		if (JkLog.silent()) {
			return "quiet";
		}
		if (JkLog.verbose()) {
			return "default";
		}
		return "download-only";
	}


	private static void commitPublication(DependencyResolver resolver) {
		try {
			resolver.commitPublishTransaction();
		} catch (final Exception e) {
			throw JkUtilsThrowable.unchecked(e);
		}
	}

	private static class CheckFileFlag {

		private JkPgp pgpSigner;

		public static CheckFileFlag of(JkPublishRepo publishRepo) {
			final CheckFileFlag flag = new CheckFileFlag();
			flag.pgpSigner = publishRepo.requirePgpSign();
			return flag;
		}

		public void publishChecks(DependencyResolver resolver, Artifact artifact, File file) throws IOException {
			if (pgpSigner != null) {
				final String ext = artifact.getExt();
				final Artifact signArtifact = withExtension(artifact, ext + ".asc");

				final File signedFile = new File(file.getPath()+".asc");
				if (!signedFile.exists()) {
					JkLog.info("Signing file " + file.getPath() + " on detached signature " +signedFile.getPath());
					pgpSigner.sign(file);
				}
				resolver.publish(signArtifact, signedFile, true);
			}
		}
	}

}