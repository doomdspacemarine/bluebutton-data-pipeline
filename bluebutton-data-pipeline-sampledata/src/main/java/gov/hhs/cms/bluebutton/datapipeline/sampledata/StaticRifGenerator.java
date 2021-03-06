package gov.hhs.cms.bluebutton.datapipeline.sampledata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import com.justdavis.karl.misc.exceptions.unchecked.UncheckedIoException;

import gov.hhs.cms.bluebutton.datapipeline.rif.model.RifFile;

/**
 * This {@link RifGenerator} just generates RIF files based on
 * static/handcrafted/presupplied RIF files stored in the classpath, as temp
 * files (which are not cleaned up by this class).
 */
public final class StaticRifGenerator implements RifGenerator {
	private final StaticRifResource[] resources;

	/**
	 * Constructs a new {@link StaticRifGenerator} instance.
	 * 
	 * @param resources
	 *            the RIF files to "generate", which must all be available at
	 *            the specified classpath locations
	 */
	public StaticRifGenerator(StaticRifResource... resources) {
		this.resources = resources;
	}

	/**
	 * @see gov.hhs.cms.bluebutton.datapipeline.sampledata.RifGenerator#generate()
	 */
	@Override
	public Stream<RifFile> generate() {
		return Stream.of(resources).map(resource -> resource.getClasspathName()).map(resource -> {
			try {
				InputStream resourceStream = Thread.currentThread().getContextClassLoader()
						.getResourceAsStream(resource);
				String resourceFileName = Paths.get(resource).getFileName().toString();
				Path outputPath = Files.createTempFile(resourceFileName, ".rif");
				Files.copy(resourceStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
				return new LocalRifFile(outputPath, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new UncheckedIoException(e);
			}
		});
	}
}
