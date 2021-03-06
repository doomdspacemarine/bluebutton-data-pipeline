package gov.hhs.cms.bluebutton.datapipeline.fhir;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.jdo.PersistenceManager;

import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.justdavis.karl.misc.datasources.provisioners.IProvisioningRequest;
import com.justdavis.karl.misc.datasources.provisioners.hsql.HsqlProvisioningRequest;

import gov.hhs.cms.bluebutton.datapipeline.ccw.jdo.AllClaimsProfile;
import gov.hhs.cms.bluebutton.datapipeline.ccw.jdo.ClaimType;
import gov.hhs.cms.bluebutton.datapipeline.ccw.jdo.CurrentBeneficiary;
import gov.hhs.cms.bluebutton.datapipeline.ccw.jdo.PartAClaimFact;
import gov.hhs.cms.bluebutton.datapipeline.ccw.jdo.PartAClaimRevLineFact;
import gov.hhs.cms.bluebutton.datapipeline.ccw.jdo.Procedure;
import gov.hhs.cms.bluebutton.datapipeline.ccw.test.CcwTestHelper;
import gov.hhs.cms.bluebutton.datapipeline.ccw.test.TearDownAcceptor;
import gov.hhs.cms.bluebutton.datapipeline.desynpuf.SynpufArchive;
import gov.hhs.cms.bluebutton.datapipeline.fhir.load.FhirLoader;
import gov.hhs.cms.bluebutton.datapipeline.fhir.load.FhirResult;
import gov.hhs.cms.bluebutton.datapipeline.fhir.transform.BeneficiaryBundle;
import gov.hhs.cms.bluebutton.datapipeline.fhir.transform.DataTransformer;
import gov.hhs.cms.bluebutton.datapipeline.sampledata.SampleDataLoader;

/**
 * Integration tests for {@link FhirLoader}.
 */
@ContextConfiguration(classes = { SpringConfigForTests.class })
@RunWith(Parameterized.class)
public final class FhirLoaderIT {
	private static final Logger LOGGER = LoggerFactory.getLogger(FhirLoaderIT.class);

	@ClassRule
	public static final SpringClassRule springClassRule = new SpringClassRule();

	@Rule
	public final SpringMethodRule springMethodRule = new SpringMethodRule();

	@Rule
	public final TearDownAcceptor tearDown = new TearDownAcceptor();

	@Inject
	public CcwTestHelper ccwHelper;

	@Parameters
	public static Iterable<Object> createTestParameters() {
		return Arrays.asList(new HsqlProvisioningRequest("tests"));
	}

	@Parameter(0)
	public IProvisioningRequest provisioningRequest;

	/**
	 * Verifies that {@link FhirLoader} works correctly when passed a small,
	 * hand-crafted data set.
	 * 
	 * @throws URISyntaxException
	 *             (won't happen: URI is hardcoded)
	 */
	@Test
	public void loadHandcraftedSample() throws URISyntaxException {
		// Use the DataTransformer to create some sample FHIR resources.
		CurrentBeneficiary beneA = new CurrentBeneficiary().setId(0).setBirthDate(LocalDate.now());
		PartAClaimFact outpatientClaimForBeneA = new PartAClaimFact().setId(0L).setBeneficiary(beneA)
				.setClaimProfile(new AllClaimsProfile().setId(1L).setClaimType(ClaimType.OUTPATIENT_CLAIM))
				.setAdmittingDiagnosisCode("foo");
		beneA.getPartAClaimFacts().add(outpatientClaimForBeneA);
		PartAClaimRevLineFact outpatientRevLineForBeneA = new PartAClaimRevLineFact().setClaim(outpatientClaimForBeneA)
				.setLineNumber(1).setRevenueCenter(new Procedure().setCode("foo"));
		outpatientClaimForBeneA.getClaimLines().add(outpatientRevLineForBeneA);
		CurrentBeneficiary beneB = new CurrentBeneficiary().setId(1).setBirthDate(LocalDate.now());
		PartAClaimFact outpatientClaimForBeneB = new PartAClaimFact().setId(1L).setBeneficiary(beneB)
				.setClaimProfile(outpatientClaimForBeneA.getClaimProfile()).setAdmittingDiagnosisCode("foo");
		beneB.getPartAClaimFacts().add(outpatientClaimForBeneB);
		PartAClaimRevLineFact outpatientRevLineForBeneB = new PartAClaimRevLineFact().setClaim(outpatientClaimForBeneB)
				.setLineNumber(1).setRevenueCenter(new Procedure().setCode("foo"));
		outpatientClaimForBeneB.getClaimLines().add(outpatientRevLineForBeneB);
		Stream<BeneficiaryBundle> fhirStream = new DataTransformer()
				.transformSourceData(Arrays.asList(beneA, beneB).stream());
		// TODO need to expand the test data here

		// Push the data to FHIR.
		URI fhirServer = new URI("http://localhost:8080/hapi-fhir/baseDstu2");
		LoadAppOptions options = new LoadAppOptions(fhirServer);
		FhirLoader loader = new FhirLoader(new MetricRegistry(), options);
		List<FhirResult> results = loader.insertFhirRecords(fhirStream);

		// Verify the results.
		Assert.assertNotNull(results);
		Assert.assertEquals(1, results.size());
		Assert.assertEquals(12, results.get(0).getResourcesPushedCount());

		// TODO verify results by actually querying the server.
	}

	/**
	 * Verifies that the entire data pipeline works correctly: all the way from
	 * populating a mock CCW schema with DE-SynPUF sample data through
	 * extracting, transform, and finally loading that data into a live FHIR
	 * server.
	 * 
	 * @throws URISyntaxException
	 *             (won't happen: URI is hardcoded)
	 */
	@Test
	public void loadSynpufDataSampleA() throws URISyntaxException {
		JDOPersistenceManagerFactory pmf = ccwHelper.provisionMockCcwDatabase(provisioningRequest, tearDown);

		try (PersistenceManager pm = pmf.getPersistenceManager();) {
			// Load the DE-SynPUF sample data and then extract it.
			MetricRegistry sampleDataMetrics = new MetricRegistry();
			SampleDataLoader sampleLoader = new SampleDataLoader(sampleDataMetrics);
			SynpufArchive archive = SynpufArchive.SAMPLE_TEST_A;
			List<CurrentBeneficiary> beneficiaries = sampleLoader.loadSampleData(Paths.get(".", "target"), archive);
			// TODO get the CcwExtractor in this pipeline
			Stream<CurrentBeneficiary> beneficiariesStream = beneficiaries.stream();

			// Transform the data.
			Stream<BeneficiaryBundle> fhirStream = new DataTransformer().transformSourceData(beneficiariesStream);

			// Setup the metrics for FhirLoader to use.
			MetricRegistry fhirMetrics = new MetricRegistry();
			fhirMetrics.registerAll(new MemoryUsageGaugeSet());
			fhirMetrics.registerAll(new GarbageCollectorMetricSet());
			Slf4jReporter fhirMetricsReporter = Slf4jReporter.forRegistry(fhirMetrics).outputTo(LOGGER).build();
			fhirMetricsReporter.start(300, TimeUnit.SECONDS);

			// Push the data to FHIR.
			// URI fhirServer = new
			// URI("http://ec2-52-4-198-86.compute-1.amazonaws.com:8081/baseDstu2");
			URI fhirServer = new URI("http://localhost:8080/hapi-fhir/baseDstu2");
			LoadAppOptions options = new LoadAppOptions(fhirServer);
			FhirLoader loader = new FhirLoader(fhirMetrics, options);
			List<FhirResult> results = loader.insertFhirRecords(fhirStream);
			LOGGER.info("FHIR resources loaded.");
			fhirMetricsReporter.stop();
			fhirMetricsReporter.report();

			// Verify the results.
			Assert.assertNotNull(results);
			Assert.assertTrue(results.stream().mapToInt(r -> r.getResourcesPushedCount()).sum() > 100);

			// TODO verify results by actually querying the server.
		}
	}
}
