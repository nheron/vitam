/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.processing.integration.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayway.restassured.RestAssured;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Filters;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.ServerIdentity;
import fr.gouv.vitam.common.SystemPropertyUtil;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.client.configuration.ClientConfigurationImpl;
import fr.gouv.vitam.common.database.builder.query.CompareQuery;
import fr.gouv.vitam.common.database.builder.query.QueryHelper;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.GLOBAL;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTION;
import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.PROJECTIONARGS;
import fr.gouv.vitam.common.database.builder.request.multiple.InsertMultiQuery;
import fr.gouv.vitam.common.database.builder.request.multiple.SelectMultiQuery;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.format.identification.FormatIdentifierFactory;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.guid.GUIDReader;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.junit.JunitHelper.ElasticsearchTestConfiguration;
import fr.gouv.vitam.common.logging.SysErrLogger;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.ProcessAction;
import fr.gouv.vitam.common.model.ProcessState;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.UpdateWorkflowConstants;
import fr.gouv.vitam.common.model.administration.AccessContractModel;
import fr.gouv.vitam.common.model.administration.AccessionRegisterDetailModel;
import fr.gouv.vitam.common.model.administration.IngestContractModel;
import fr.gouv.vitam.common.model.administration.ProfileModel;
import fr.gouv.vitam.common.model.administration.RegisterValueDetailModel;
import fr.gouv.vitam.common.model.logbook.LogbookEventOperation;
import fr.gouv.vitam.common.model.logbook.LogbookOperation;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.common.AccessionRegisterSummary;
import fr.gouv.vitam.functional.administration.rest.AdminManagementMain;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.parameters.Contexts;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.logbook.rest.LogbookMain;
import fr.gouv.vitam.metadata.client.MetaDataClient;
import fr.gouv.vitam.metadata.client.MetaDataClientFactory;
import fr.gouv.vitam.metadata.core.UnitInheritedRule;
import fr.gouv.vitam.metadata.rest.MetadataMain;
import fr.gouv.vitam.processing.common.ProcessingEntry;
import fr.gouv.vitam.processing.common.exception.ProcessingStorageWorkspaceException;
import fr.gouv.vitam.processing.common.model.ProcessWorkflow;
import fr.gouv.vitam.processing.data.core.ProcessDataAccessImpl;
import fr.gouv.vitam.processing.data.core.management.ProcessDataManagement;
import fr.gouv.vitam.processing.data.core.management.WorkspaceProcessDataManagement;
import fr.gouv.vitam.processing.engine.core.monitoring.ProcessMonitoringImpl;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClient;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClientFactory;
import fr.gouv.vitam.processing.management.rest.ProcessManagementMain;
import fr.gouv.vitam.worker.core.plugin.CheckExistenceObjectPlugin;
import fr.gouv.vitam.worker.core.plugin.CheckIntegrityObjectPlugin;
import fr.gouv.vitam.worker.server.rest.WorkerMain;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import fr.gouv.vitam.workspace.rest.WorkspaceMain;
import org.bson.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.jayway.restassured.RestAssured.get;
import static fr.gouv.vitam.logbook.common.server.database.collections.LogbookDocument.EVENT_DETAILS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Processing integration test
 */
public class ProcessingIT {
    private static final String PROCESSING_UNIT_PLAN = "integration-processing/unit_plan_metadata.json";
    private static final String INGEST_CONTRACTS_PLAN = "integration-processing/ingest_contracts_plan.json";
    private static final String ACCESS_CONTRACT =
        "integration-processing/access_contract_every_originating_angency.json";
    private static final String UNIT_ATTACHEMENT_ID = "aeaqaaaaaagbcaacaang6ak4ts6paliaaaaq";
    private static final String OG_ATTACHEMENT_ID = "aebaaaaaaacu6xzeabinwak6t5ecmmaaaaaq";
    private static final String UNIT_PLAN_ATTACHEMENT_ID = "aeaqaaaaaagbcaacabht2ak4x66x2baaaaaq";
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ProcessingIT.class);
    private static final int DATABASE_PORT = 12346;
    private static final long SLEEP_TIME = 100l;
    private static final long NB_TRY = 4800; // equivalent to 4 minutes
    private static MongodExecutable mongodExecutable;
    static MongodProcess mongod;
    static MongoClient mongoClient;

    private static final Integer tenantId = 0;

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final static String CLUSTER_NAME = "vitam-cluster";
    static JunitHelper junitHelper;
    private static int TCP_PORT = 54321;
    private static int HTTP_PORT = 54320;

    private static final int PORT_SERVICE_WORKER = 8098;
    private static final int PORT_SERVICE_WORKSPACE = 8094;
    private static final int PORT_SERVICE_METADATA = 8096;
    private static final int PORT_SERVICE_PROCESSING = 8097;
    private static final int PORT_SERVICE_FUNCTIONAL_ADMIN = 8093;
    private static final int PORT_SERVICE_LOGBOOK = 8099;

    private static final String SIP_FOLDER = "SIP";
    private static final String METADATA_PATH = "/metadata/v1";
    private static final String PROCESSING_PATH = "/processing/v1";
    private static final String WORKER_PATH = "/worker/v1";
    private static final String WORKSPACE_PATH = "/workspace/v1";
    private static final String LOGBOOK_PATH = "/logbook/v1";

    private static String CONFIG_WORKER_PATH = "";
    private static String CONFIG_BIG_WORKER_PATH = "";
    private static String CONFIG_WORKSPACE_PATH = "";
    private static String CONFIG_METADATA_PATH = "";
    private static String CONFIG_PROCESSING_PATH = "";
    private static String CONFIG_FUNCTIONAL_ADMIN_PATH = "";
    private static String CONFIG_FUNCTIONAL_CLIENT_PATH = "";
    private static String CONFIG_LOGBOOK_PATH = "";
    private static String CONFIG_SIEGFRIED_PATH = "";

    // private static VitamServer workerApplication;
    private static MetadataMain metadataMain;
    private static WorkerMain workerApplication;
    private static AdminManagementMain adminApplication;
    private static LogbookMain logbookApplication;
    private static ProcessManagementMain processManagementApplication;
    private static WorkspaceMain workspaceMain;
    private WorkspaceClient workspaceClient;
    private ProcessingManagementClient processingClient;
    private static ProcessMonitoringImpl processMonitoring;

    private static final String WORKSPACE_URL = "http://localhost:" + PORT_SERVICE_WORKSPACE;
    private static final String PROCESSING_URL = "http://localhost:" + PORT_SERVICE_PROCESSING;

    private static String WORFKLOW_NAME_2 = "PROCESS_SIP_UNITARY";
    private static String WORFKLOW_NAME = "PROCESS_SIP_UNITARY";
    private static String BLANK_WORKFLOW_NAME = "PROCESS_SIP_UNITARY_TEST";
    private static String INGEST_TREE_WORFKLOW = "HOLDINGSCHEME";
    private static String INGEST_PLAN_WORFKLOW = "FILINGSCHEME";
    private static String BIG_WORFKLOW_NAME = "BigIngestWorkflow";
    private static String UPD8_AU_WORKFLOW = "UPDATE_RULES_ARCHIVE_UNITS";
    private static String LFC_TRACEABILITY_WORKFLOW = "LOGBOOK_LC_SECURISATION";
    private static String SIP_FILE_OK_NAME = "integration-processing/SIP-test.zip";
    private static String  SIP_FILE_OK_BIRTH_PLACE = "integration-processing/unit_schema_validation_ko.zip";
    private static String SIP_PROFIL_OK = "integration-processing/SIP_ok_profil.zip";
    private static String SIP_INGEST_CONTRACT_UNKNOW = "integration-processing/SIP_INGEST_CONTRACT_UNKNOW.zip";
    private static String SIP_FILE_OK_WITH_SYSTEMID = "integration-processing/SIP_with_systemID.zip";
    // TODO : use for IT test to add a link between two AUs (US 1686)

    // TODO : use for IT test to add a link between two AUs (US 1686)

    private static String SIP_FILE_ADD_AU_LINK_OK_NAME_TARGET = "integration-processing";
    private static String SIP_FILE_ADD_AU_LINK_OK_NAME = "integration-processing/OK_SIP_ADD_AU_LINK";

    private static String LINK_AU_TO_EXISTING_GOT_OK_NAME = "integration-processing/OK_LINK_AU_TO_EXISTING_GOT";
    private static String LINK_AU_TO_EXISTING_GOT_OK_NAME_TARGET = "integration-processing";
    private static String SIP_FILE_TAR_OK_NAME = "integration-processing/SIP.tar";
    private static String SIP_INHERITED_RULE_CA1_OK = "integration-processing/1069_CA1.zip";
    private static String SIP_INHERITED_RULE_CA4_OK = "integration-processing/1069_CA4.zip";
    private static String SIP_ARBO_COMPLEXE_FILE_OK = "integration-processing/OK-registre-fonds.zip";
    private static String SIP_FUND_REGISTER_OK = "integration-processing/OK-registre-fonds.zip";
    private static String SIP_WITHOUT_MANIFEST = "integration-processing/SIP_no_manifest.zip";
    private static String SIP_NO_FORMAT = "integration-processing/SIP_NO_FORMAT.zip";
    private static String SIP_DOUBLE_BM = "integration-processing/SIP_DoubleBM.zip";
    private static String SIP_NO_FORMAT_NO_TAG = "integration-processing/SIP_NO_FORMAT_TAG.zip";
    private static String SIP_NB_OBJ_INCORRECT_IN_MANIFEST = "integration-processing/SIP_Conformity_KO.zip";
    private static String SIP_ORPHELINS = "integration-processing/SIP-orphelins.zip";
    private static String SIP_CYCLE = "integration-processing/SIP-cycle.zip";
    private static String SIP_OBJECT_SANS_GOT = "integration-processing/SIP-objetssansGOT.zip";
    private static String SIP_BUG_2721 = "integration-processing/bug2721_2racines_meme_rattachement.zip";
    private static String SIP_WITHOUT_OBJ = "integration-processing/OK_SIP_sans_objet.zip";
    private static String SIP_WITHOUT_FUND_REGISTER = "integration-processing/KO_registre_des_fonds.zip";
    private static String SIP_BORD_AU_REF_PHYS_OBJECT = "integration-processing/KO_BORD_AUrefphysobject.zip";
    private static String SIP_MANIFEST_INCORRECT_REFERENCE = "integration-processing/KO_Reference_Unexisting.zip";
    private static String SIP_REFERENCE_CONTRACT_KO = "integration-processing/KO_SIP_2_GO_contract.zip";
    private static String SIP_COMPLEX_RULES = "integration-processing/OK_RULES_COMPLEXE_COMPLETE.zip";

    private static String SIP_FILE_KO_AU_REF_BDO = "integration-processing/SIP_KO_ArchiveUnit_ref_BDO.zip";
    private static String SIP_BUG_2182 = "integration-processing/SIP_bug_2182.zip";
    private static String SIP_ARBRE = "integration-processing/test_arbre.zip";
    private static String SIP_PLAN = "integration-processing/test_plan.zip";
    private static String SIP_FILE_1791_CA1 = "integration-processing/SIP_FILE_1791_CA1.zip";
    private static String SIP_FILE_1791_CA2 = "integration-processing/SIP_FILE_1791_CA2.zip";
    private static String OK_SIP_2_GO = "integration-processing/OK_SIP_2_GO.zip";

    private static String SIP_ARBRE_3062 = "integration-processing/3062_arbre.zip";

    private static String SIP_PROD_SERV_A = "integration-processing/Sip_A.zip";
    private static String SIP_PROD_SERV_B_ATTACHED = "integration-processing/SIP_B";

    private static ElasticsearchTestConfiguration config = null;

    private final static String DUMMY_REQUEST_ID = "reqId";
    private static boolean imported = false;
    private static String defautDataFolder = VitamConfiguration.getVitamDataFolder();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        VitamConfiguration.getConfiguration()
            .setData(PropertiesUtils.getResourcePath("integration-processing/").toString());
        CONFIG_METADATA_PATH = PropertiesUtils.getResourcePath("integration-processing/metadata.conf").toString();
        CONFIG_WORKER_PATH = PropertiesUtils.getResourcePath("integration-processing/worker.conf").toString();
        CONFIG_BIG_WORKER_PATH = PropertiesUtils.getResourcePath("integration-processing/bigworker.conf").toString();
        CONFIG_WORKSPACE_PATH = PropertiesUtils.getResourcePath("integration-processing/workspace.conf").toString();
        CONFIG_PROCESSING_PATH = PropertiesUtils.getResourcePath("integration-processing/processing.conf").toString();
        CONFIG_SIEGFRIED_PATH =
            PropertiesUtils.getResourcePath("integration-processing/format-identifiers.conf").toString();
        CONFIG_FUNCTIONAL_ADMIN_PATH =
            PropertiesUtils.getResourcePath("integration-processing/functional-administration.conf").toString();
        CONFIG_FUNCTIONAL_CLIENT_PATH =
            PropertiesUtils.getResourcePath("integration-processing/functional-administration-client-it.conf")
                .toString();

        CONFIG_LOGBOOK_PATH = PropertiesUtils.getResourcePath("integration-processing/logbook.conf").toString();
        CONFIG_SIEGFRIED_PATH =
            PropertiesUtils.getResourcePath("integration-processing/format-identifiers.conf").toString();

        File tempFolder = temporaryFolder.newFolder();
        System.setProperty("vitam.tmp.folder", tempFolder.getAbsolutePath());

        SystemPropertyUtil.refresh();


        // ES
        config = JunitHelper.startElasticsearchForTest(temporaryFolder, CLUSTER_NAME, TCP_PORT, HTTP_PORT);

        final MongodStarter starter = MongodStarter.getDefaultInstance();

        mongodExecutable = starter.prepare(new MongodConfigBuilder()
            .withLaunchArgument("--enableMajorityReadConcern")
            .version(Version.Main.PRODUCTION)
            .net(new Net(DATABASE_PORT, Network.localhostIsIPv6()))
            .build());
        mongod = mongodExecutable.start();

        mongoClient = new MongoClient(new ServerAddress("localhost", DATABASE_PORT));
        // launch metadata
        SystemPropertyUtil.set(MetadataMain.PARAMETER_JETTY_SERVER_PORT,
            Integer.toString(PORT_SERVICE_METADATA));
        metadataMain = new MetadataMain(CONFIG_METADATA_PATH);
        metadataMain.start();
        SystemPropertyUtil.clear(MetadataMain.PARAMETER_JETTY_SERVER_PORT);

        MetaDataClientFactory.changeMode(new ClientConfigurationImpl("localhost", PORT_SERVICE_METADATA));

        // launch workspace
        SystemPropertyUtil.set(WorkspaceMain.PARAMETER_JETTY_SERVER_PORT,
            Integer.toString(PORT_SERVICE_WORKSPACE));
        workspaceMain = new WorkspaceMain(CONFIG_WORKSPACE_PATH);
        workspaceMain.start();
        SystemPropertyUtil.clear(WorkspaceMain.PARAMETER_JETTY_SERVER_PORT);

        WorkspaceClientFactory.changeMode(WORKSPACE_URL);

        // launch logbook
        SystemPropertyUtil
            .set(LogbookMain.PARAMETER_JETTY_SERVER_PORT, Integer.toString(PORT_SERVICE_LOGBOOK));
        logbookApplication = new LogbookMain(CONFIG_LOGBOOK_PATH);
        logbookApplication.start();
        SystemPropertyUtil.clear(LogbookMain.PARAMETER_JETTY_SERVER_PORT);

        LogbookOperationsClientFactory.changeMode(new ClientConfigurationImpl("localhost", PORT_SERVICE_LOGBOOK));
        LogbookLifeCyclesClientFactory.changeMode(new ClientConfigurationImpl("localhost", PORT_SERVICE_LOGBOOK));

        // launch processing
        SystemPropertyUtil.set(ProcessManagementMain.PARAMETER_JETTY_SERVER_PORT,
            Integer.toString(PORT_SERVICE_PROCESSING));
        processManagementApplication = new ProcessManagementMain(CONFIG_PROCESSING_PATH);
        processManagementApplication.start();
        SystemPropertyUtil.clear(ProcessManagementMain.PARAMETER_JETTY_SERVER_PORT);

        ProcessingManagementClientFactory.changeConfigurationUrl(PROCESSING_URL);

        // launch worker
        SystemPropertyUtil.set("jetty.worker.port", Integer.toString(PORT_SERVICE_WORKER));
        workerApplication = new WorkerMain(CONFIG_WORKER_PATH);
        workerApplication.start();
        SystemPropertyUtil.clear("jetty.worker.port");

        FormatIdentifierFactory.getInstance().changeConfigurationFile(CONFIG_SIEGFRIED_PATH);

        // launch functional Admin server
        adminApplication = new AdminManagementMain(CONFIG_FUNCTIONAL_ADMIN_PATH);
        adminApplication.start();

        AdminManagementClientFactory
            .changeMode(new ClientConfigurationImpl("localhost", PORT_SERVICE_FUNCTIONAL_ADMIN));


        processMonitoring = ProcessMonitoringImpl.getInstance();

    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        VitamConfiguration.getConfiguration().setData(defautDataFolder);
        if (config != null) {
            JunitHelper.stopElasticsearchForTest(config);
        }

        mongod.stop();
        mongodExecutable.stop();

        try {
            workspaceMain.stop();
            adminApplication.stop();
            workerApplication.stop();
            logbookApplication.stop();
            processManagementApplication.stop();
            metadataMain.stop();
            mongoClient.close();
        } catch (final Exception e) {
            LOGGER.error(e);
        }
    }


    @After
    public void afterTest() {
        MongoDatabase db = mongoClient.getDatabase("Vitam");
        db.getCollection("Unit").deleteMany(new Document());
        db.getCollection("ObjectGroup").deleteMany(new Document());
        db.getCollection("AccessionRegisterSummary").deleteMany(new Document());
        db.getCollection("LogbookOperation").deleteMany(new Document());
    }

    @Test
    public void testServersStatus() throws Exception {
        try {
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());

            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());

            RestAssured.port = PORT_SERVICE_METADATA;
            RestAssured.basePath = METADATA_PATH;
            get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());

            RestAssured.port = PORT_SERVICE_WORKER;
            RestAssured.basePath = WORKER_PATH;
            get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());

            RestAssured.port = PORT_SERVICE_LOGBOOK;
            RestAssured.basePath = LOGBOOK_PATH;
            get("/status").then().statusCode(Status.NO_CONTENT.getStatusCode());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    private void tryImportFile() {
        VitamThreadUtils.getVitamSession().setContractId("aName");
        flush();

        if (!imported) {
            try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
                client.importFormat(
                    PropertiesUtils.getResourceAsStream("integration-processing/DROID_SignatureFile_V88.xml"),
                    "DROID_SignatureFile_V88.xml");
                // Import Rules
                client.importRulesFile(
                    PropertiesUtils.getResourceAsStream("integration-processing/jeu_donnees_OK_regles_CSV_regles.csv"),
                    "jeu_donnees_OK_regles_CSV_regles.csv");

                client.importAgenciesFile(PropertiesUtils.getResourceAsStream("agencies.csv"), "agencies.csv");
                // lets check evdetdata for rules import
                LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
                fr.gouv.vitam.common.database.builder.request.single.Select selectQuery =
                    new fr.gouv.vitam.common.database.builder.request.single.Select();
                selectQuery.setQuery(QueryHelper.eq("evType", "STP_IMPORT_RULES"));
                JsonNode logbookResult = logbookClient.selectOperation(selectQuery.getFinalSelect());
                assertNotNull(logbookResult.get("$results").get(0).get("evDetData"));
                assertTrue(JsonHandler.writeAsString(logbookResult.get("$results").get(0).get("evDetData"))
                    .contains("jeu_donnees_OK_regles_CSV_regles"));

                File fileProfiles = PropertiesUtils.getResourceFile("integration-processing/OK_profil.json");
                List<ProfileModel> profileModelList =
                    JsonHandler.getFromFileAsTypeRefence(fileProfiles, new TypeReference<List<ProfileModel>>() {});
                RequestResponse improrResponse = client.createProfiles(profileModelList);

                RequestResponseOK<ProfileModel> response =
                    (RequestResponseOK<ProfileModel>) client.findProfiles(new Select().getFinalSelect());
                client.importProfileFile(response.getResults().get(0).getIdentifier(),
                    PropertiesUtils.getResourceAsStream("integration-processing/Profil20.rng"));

                // import contract
                File fileContracts =
                    PropertiesUtils.getResourceFile("integration-processing/referential_contracts_ok.json");
                List<IngestContractModel> IngestContractModelList = JsonHandler.getFromFileAsTypeRefence(fileContracts,
                    new TypeReference<List<IngestContractModel>>() {});

                Status importStatus = client.importIngestContracts(IngestContractModelList);

                // import access contract
                File fileAccessContracts = PropertiesUtils.getResourceFile(ACCESS_CONTRACT);
                List<AccessContractModel> accessContractModelList = JsonHandler
                    .getFromFileAsTypeRefence(fileAccessContracts, new TypeReference<List<AccessContractModel>>() {});
                client.importAccessContracts(accessContractModelList);
            } catch (final Exception e) {
                LOGGER.error(e);
            }
            imported = true;
        }
    }

    private void flush() {
        ProcessDataAccessImpl.getInstance().clearWorkflow();
    }

    private void wait(String operationId) {
        int nbTry = 0;
        while (!processingClient.isOperationCompleted(operationId)) {
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                SysErrLogger.FAKE_LOGGER.ignoreLog(e);
            }
            if (nbTry == NB_TRY)
                break;
            nbTry++;
        }
    }

    /**
     * This test needs Siegfried already running and started as:<br/>
     * sf -server localhost:8999<br/>
     * <br/>
     * If not started, this test will be ignored.
     *
     * @throws Exception
     */
    @Test
    public void testTryWithSiegfried() throws Exception {
        final String CONFIG_SIEGFRIED_PATH_REAL =
            PropertiesUtils.getResourcePath("integration-processing/format-identifiers-real.conf").toString();
        try {
            FormatIdentifierFactory.getInstance().changeConfigurationFile(CONFIG_SIEGFRIED_PATH_REAL);
            FormatIdentifierFactory.getInstance().getFormatIdentifierFor("siegfried-local").status();
            testWorkflow();
        } catch (final Exception e) {
            // Ignore
            SysErrLogger.FAKE_LOGGER.ignoreLog(e);
            Assume.assumeTrue("Real Siegfried not running", false);
        } finally {
            FormatIdentifierFactory.getInstance().changeConfigurationFile(CONFIG_SIEGFRIED_PATH);
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflow() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        try (MetaDataClient metaDataClient = MetaDataClientFactory.getInstance().getClient();
            LogbookLifeCyclesClient logbookLFCClient = LogbookLifeCyclesClientFactory.getInstance().getClient();
            AdminManagementClient functionalClient = AdminManagementClientFactory.getInstance().getClient()) {
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject = PropertiesUtils.getResourceAsStream(SIP_BUG_2721);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            metaDataClient.insertUnit(
                new InsertMultiQuery()
                    .addData((ObjectNode) JsonHandler
                        .getFromFile(PropertiesUtils.getResourceFile("integration-processing/unit_metadata.json")))
                    .getFinalInsert());

            metaDataClient.insertUnit(
                new InsertMultiQuery()
                    .addData(
                        (ObjectNode) JsonHandler.getFromFile(PropertiesUtils.getResourceFile(PROCESSING_UNIT_PLAN)))
                    .getFinalInsert());

            metaDataClient.flushUnits();
            // import contract
            File fileContracts = PropertiesUtils.getResourceFile(INGEST_CONTRACTS_PLAN);
            List<IngestContractModel> IngestContractModelList =
                JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<IngestContractModel>>() {});

            functionalClient.importIngestContracts(IngestContractModelList);

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow = processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());

            LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
            fr.gouv.vitam.common.database.builder.request.single.Select selectQuery =
                new fr.gouv.vitam.common.database.builder.request.single.Select();
            selectQuery.setQuery(QueryHelper.eq("evIdProc", containerName));
            JsonNode logbookResult = logbookClient.selectOperation(selectQuery.getFinalSelect());

            assertEquals(logbookResult.get("$results").get(0).get("events").get(0).get("outDetail").asText(),
                "STP_INGEST_FINALISATION.OK");

            assertEquals(logbookResult.get("$results").get(0).get("obIdIn").asText(),
                "bug2721_2racines_meme_rattachement");

            JsonNode agIdExt = JsonHandler.getFromString(logbookResult.get("$results").get(0).get("agIdExt").asText());
            assertEquals(agIdExt.get("originatingAgency").asText(), "producteur1");

        } catch (final Exception e) {
            LOGGER.error(e);
            fail("should not raized an exception" + e);
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testAudit() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        try (MetaDataClient metaDataClient = MetaDataClientFactory.getInstance().getClient();
            LogbookLifeCyclesClient logbookLFCClient = LogbookLifeCyclesClientFactory.getInstance().getClient();
            LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
            AdminManagementClient functionalClient = AdminManagementClientFactory.getInstance().getClient()) {

            tryImportFile();

            metaDataClient.insertObjectGroup(
                new InsertMultiQuery()
                    .addData((ObjectNode) JsonHandler
                        .getFromFile(PropertiesUtils.getResourceFile("integration-processing/og_metadata.json")))
                    .getFinalInsert());

            GUID logLfcId = GUIDFactory.newOperationLogbookGUID(tenantId);
            logbookLFCClient.create(
                LogbookParametersFactory.newLogbookLifeCycleObjectGroupParameters(
                    logLfcId,
                    "INGEST",
                    logLfcId,
                    LogbookTypeProcess.INGEST,
                    StatusCode.OK,
                    "Process_SIP_unitary.OK",
                    OG_ATTACHEMENT_ID,
                    GUIDReader.getGUID(OG_ATTACHEMENT_ID)));
            logbookLFCClient.commitObjectGroup(logLfcId.getId(), OG_ATTACHEMENT_ID);
            GUID opIngestId = GUIDFactory.newOperationLogbookGUID(tenantId);

            LogbookOperationParameters newLogbookOperationParameters =
                LogbookParametersFactory.newLogbookOperationParameters(
                    opIngestId,
                    "PROCESS_SIP_UNITARY",
                    opIngestId,
                    LogbookTypeProcess.INGEST,
                    StatusCode.STARTED,
                    "PROCESS_SIP_UNITARY.STARTED",
                    opIngestId);

            newLogbookOperationParameters.putParameterValue(
                LogbookParameterName.agIdExt, "{\"originatingAgency\":\"Vitam\"}");
            logbookClient.create(newLogbookOperationParameters);
            newLogbookOperationParameters.putParameterValue(
                LogbookParameterName.outcomeDetail, "PROCESS_SIP_UNITARY.OK");

            logbookClient.update(newLogbookOperationParameters);

            AccessionRegisterDetailModel register = new AccessionRegisterDetailModel();
            register.setOriginatingAgency("Vitam");
            register.setTotalObjects(new RegisterValueDetailModel(1, 0, 0));
            register.setTotalObjectsGroups(new RegisterValueDetailModel(1, 0, 0));
            register.setTotalUnits(new RegisterValueDetailModel(1, 0, 0));
            register.setObjectSize(new RegisterValueDetailModel(1, 0, 0));
            register.setEndDate("01/01/2017");
            register.setStartDate("01/01/2017");
            register.setLastUpdate("01/01/2017");
            functionalClient.createorUpdateAccessionRegister(register);

            // Test Audit
            final GUID opId = GUIDFactory.newRequestIdGUID(tenantId);
            final String auditId = opId.toString();

            final GUID operationAuditGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationAuditGuid);
            createLogbookOperation(operationAuditGuid, opId, "PROCESS_AUDIT", LogbookTypeProcess.AUDIT);
            final ProcessingEntry entry = new ProcessingEntry(auditId, Contexts.AUDIT_WORKFLOW.getEventType());
            entry.getExtraParams().put("objectId", "0");
            entry.getExtraParams().put("auditType", "tenant");
            entry.getExtraParams().put("auditActions",
                CheckExistenceObjectPlugin.getId() + "," + CheckIntegrityObjectPlugin.getId());
            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.AUDIT_WORKFLOW.name(), entry);

            RequestResponse<ItemStatus> auditResponse =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), auditId);

            wait(auditId);

            ProcessWorkflow processAuditWorkflow = processMonitoring.findOneProcessWorkflow(auditId, tenantId);
            assertNotNull(processAuditWorkflow);
            assertEquals(ProcessState.COMPLETED, processAuditWorkflow.getState());
            assertEquals(StatusCode.OK, processAuditWorkflow.getStatus());
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("should not raized an exception" + e);
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowIngestContractUnknow() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);
        
            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_INGEST_CONTRACT_UNKNOW);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());

            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());

            LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
            fr.gouv.vitam.common.database.builder.request.single.Select selectQuery =
                new fr.gouv.vitam.common.database.builder.request.single.Select();
            JsonNode logbookResult = logbookClient.selectOperationById(containerName, selectQuery.getFinalSelect());
            JsonNode logbookNode = logbookResult.get("$results").get(0);
            assertEquals(logbookNode.get("events").get(7).get("outDetail").asText(), "CHECK_HEADER.CHECK_CONTRACT_INGEST.UNKNOWN.KO");
        
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }
    
    @RunWithCustomExecutor
    @Test
    public void testWorkflowProfil() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_PROFIL_OK);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());

            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            // FIXME : the status is FATAL (STP_ACCESSION_REGISTRATION : IllegalArgumentException: Tenant id should be filled) 
            // Should check that state is COMPLETE and status is OK, Actually state is set to PAUSE (#3176)
            //assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            //assertEquals(StatusCode.OK, processWorkflow.getStatus());

            LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
            fr.gouv.vitam.common.database.builder.request.single.Select selectQuery =
                new fr.gouv.vitam.common.database.builder.request.single.Select();
            selectQuery.setQuery(QueryHelper.eq("evIdProc", containerName));
            JsonNode logbookResult = logbookClient.selectOperation(selectQuery.getFinalSelect());
            JsonNode logbookNode = logbookResult.get("$results").get(0);
            assertEquals(logbookNode.get("obIdIn").asText(),
                "Transfert des enregistrements des délibérations de l'assemblée départementale");
            JsonNode agIdExt = JsonHandler.getFromString(logbookNode.get("agIdExt").asText());

            assertEquals(agIdExt.get("submissionAgency").asText(), "https://demo.logilab.fr/seda/157118");
            assertEquals(agIdExt.get("originatingAgency").asText(), "https://demo.logilab.fr/seda/157118");
            assertEquals(agIdExt.get("ArchivalAgency").asText(), "https://demo.logilab.fr/seda/213109");
            assertEquals(agIdExt.get("TransferringAgency").asText(), "https://demo.logilab.fr/seda/157118");

            assertTrue(logbookNode.get("evDetData").asText().contains("EvDetailReq"));
            assertTrue(logbookNode.get("evDetData").asText().contains("EvDateTimeReq"));
            assertTrue(logbookNode.get("evDetData").asText().contains("ArchivalAgreement"));
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowIngestTree() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_ARBRE);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.HOLDING_SCHEME.name(), containerName, INGEST_TREE_WORFKLOW);
            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
            assertNotNull(ret);

            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.OK, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowIngestPlan() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_PLAN);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, INGEST_PLAN_WORFKLOW);
            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
            assertNotNull(ret);

            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.OK, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithSIPContainsSystemId() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_FILE_OK_WITH_SYSTEMID);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertThat(processWorkflow).isNotNull();
            assertThat(processWorkflow.getState()).isEqualTo(ProcessState.COMPLETED);
            assertThat(processWorkflow.getStatus()).isEqualTo(StatusCode.WARNING);
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithTarSIP() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);
            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;

            final InputStream zipInputStreamSipObject =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(SIP_FILE_TAR_OK_NAME);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.TAR,
                zipInputStreamSipObject);

            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());

            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflow_with_complexe_unit_seda() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;

            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_ARBO_COMPLEXE_FILE_OK);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());

            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflow_with_herited_ruleCA1() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;
            processingClient = ProcessingManagementClientFactory.getInstance().getClient();

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;


            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_INHERITED_RULE_CA1_OK);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME_2);
            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME_2,
                    Contexts.DEFAULT_WORKFLOW.name(),
                    ProcessAction.RESUME.getValue());
            assertNotNull(ret);

            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());

            MetaDataClient metaDataClient = MetaDataClientFactory.getInstance().getClient();
            SelectMultiQuery query = new SelectMultiQuery();
            query.addQueries(QueryHelper.eq("Title", "AU4").setRelativeDepthLimit(5));
            query.addProjection(JsonHandler.createObjectNode().set(PROJECTION.FIELDS.exactToken(),
                JsonHandler.createObjectNode()
                    .put(GLOBAL.RULES.exactToken(), 1).put("Title", 1)
                    .put(PROJECTIONARGS.MANAGEMENT.exactToken(), 1)));
            JsonNode result = metaDataClient.selectUnits(query.getFinalSelect());
            assertNotNull(result.get("$results").get(0).get(UnitInheritedRule.INHERITED_RULE).get("StorageRule")
                .get("R1"));
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflow_with_herited_ruleCA4() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;

            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_INHERITED_RULE_CA4_OK);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;
            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());

            MetaDataClient metaDataClient = MetaDataClientFactory.getInstance().getClient();
            SelectMultiQuery query = new SelectMultiQuery();
            query.addQueries(QueryHelper.eq("Title", "AU4").setRelativeDepthLimit(5));
            query.addProjection(JsonHandler.createObjectNode().set(PROJECTION.FIELDS.exactToken(),
                JsonHandler.createObjectNode()
                    .put(GLOBAL.RULES.exactToken(), 1).put("Title", 1)
                    .put(PROJECTIONARGS.MANAGEMENT.exactToken(), 1)));
            JsonNode result = metaDataClient.selectUnits(query.getFinalSelect());
            assertNotNull(result.get("$results").get(0).get(UnitInheritedRule.INHERITED_RULE).get("StorageRule")
                .get("R1"));
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflow_with_accession_register() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;

            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_FUND_REGISTER_OK);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithSipNoManifest() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_WITHOUT_MANIFEST);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowSipNoFormat() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;

            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_NO_FORMAT);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowSipDoubleVersionBM() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_DOUBLE_BM);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);
        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowSipNoFormatNoTag() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_NO_FORMAT_NO_TAG);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);
        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;

        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }


    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithManifestIncorrectObjectNumber() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_NB_OBJ_INCORRECT_IN_MANIFEST);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        // /////
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);

        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithOrphelins() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_ORPHELINS);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithCycle() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_CYCLE);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }


    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithoutObjectGroups() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;

            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_OBJECT_SANS_GOT);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithSipWithoutObject() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_WITHOUT_OBJ);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME, Contexts.DEFAULT_WORKFLOW.name(),
                ProcessAction.RESUME.getValue());
        assertNotNull(ret);

        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);

        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);

        // check conformity in warning state
        // File format warning state
        assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        // completed execution status
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        // checkMonitoring - meaning something has been added in the monitoring tool

    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowKOwithATRKOFilled() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_WITHOUT_FUND_REGISTER);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);

        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }

    @RunWithCustomExecutor
    // as now errors with xml are handled in ExtractSeda (not a FATAL but a KO
    // it s no longer an exception that is obtained
    @Test
    public void testWorkflowSipCausesFatalThenProcessingInternalServerException() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_BORD_AU_REF_PHYS_OBJECT);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }

    @Test
    @RunWithCustomExecutor
    public void should_export_dip() throws Exception {
        // Given
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();

        GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        String containerName = objectGuid.getId();
        VitamThreadUtils.getVitamSession().setRequestId(objectGuid);

        createLogbookOperation(operationGuid, objectGuid);

        WorkspaceClient workspaceClient = WorkspaceClientFactory.getInstance().getClient();

        // upload SIP
        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_FUND_REGISTER_OK);
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
            zipInputStreamSipObject);
        // call processing

        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        RequestResponse<ItemStatus> ret =
            processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
        assertNotNull(ret);

        assertEquals(Response.Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);

        Select select = new Select();
        CompareQuery eq = QueryHelper.eq("#operations", containerName);
        select.setQuery(eq);

        ObjectNode finalSelect = select.getFinalSelect();

        operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        objectGuid = GUIDFactory.newManifestGUID(tenantId);
        containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        workspaceClient.createContainer(containerName);

        workspaceClient.putObject(containerName, "query.json", JsonHandler.writeToInpustream(finalSelect));

        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.EXPORT_DIP.name(), containerName, "EXPORT_DIP");

        // When
        RequestResponse<JsonNode> jsonNodeRequestResponse =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());

        // Then
        assertNotNull(jsonNodeRequestResponse);
        assertEquals(Response.Status.ACCEPTED.getStatusCode(), jsonNodeRequestResponse.getStatus());

        wait(containerName);
    }


    // Status Warn
    @RunWithCustomExecutor
    @Test
    public void testWorkflowAddAndLinkSIP() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        // 1. First we create an AU by sip
        try {
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_FILE_OK_NAME);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());

            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }

        String zipPath = null;
        // 2. then we link another SIP to it
        String zipName = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 1) + ".zip";

        // prepare zip
        final MongoDatabase db = mongoClient.getDatabase("Vitam");
        MongoIterable<Document> resultUnits = db.getCollection("Unit").find();
        Document unit = resultUnits.first();
        String idUnit = (String) unit.get("_id");
        replaceStringInFile(SIP_FILE_ADD_AU_LINK_OK_NAME + "/manifest.xml", "(?<=<SystemId>).*?(?=</SystemId>)",
            idUnit);
        zipPath = PropertiesUtils.getResourcePath(SIP_FILE_ADD_AU_LINK_OK_NAME_TARGET).toAbsolutePath().toString() +
            "/" + zipName;
        zipFolder(PropertiesUtils.getResourcePath(SIP_FILE_ADD_AU_LINK_OK_NAME), zipPath);


        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;
        // use link sip
        final InputStream zipStream = new FileInputStream(new File(
            PropertiesUtils.getResourcePath(SIP_FILE_ADD_AU_LINK_OK_NAME_TARGET).toAbsolutePath() +
                "/" + zipName));

        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
            zipStream);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;

        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow = processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        assertNotNull(processWorkflow.getSteps());

        // check results
        MongoIterable<Document> modifiedParentUnit = db.getCollection("Unit").find(Filters.eq("_id", idUnit));
        assertNotNull(modifiedParentUnit);
        assertNotNull(modifiedParentUnit.first());
        Document parentUnit = modifiedParentUnit.first();

        // aeaqaaaaaad44i2vabiwgak4y2bxdkiaaaaq

        MongoIterable<Document> newChildUnit = db.getCollection("Unit").find(Filters.eq("_up", idUnit));
        assertNotNull(newChildUnit);
        assertNotNull(newChildUnit.first());
        try {
            Files.delete(new File(zipPath).toPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowAddAndLinkSIPKo() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        // We link to a non existing unit
        String zipPath = null;
        String zipName = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 1) + ".zip";

        // prepare zip
        replaceStringInFile(SIP_FILE_ADD_AU_LINK_OK_NAME + "/manifest.xml", "(?<=<SystemId>).*?(?=</SystemId>)",
            "aeaaaaaaaaaam7mxabxccakzrw47heqaaaaq");

        zipPath = PropertiesUtils.getResourcePath(SIP_FILE_ADD_AU_LINK_OK_NAME_TARGET).toAbsolutePath().toString() +
            "/" + zipName;
        zipFolder(PropertiesUtils.getResourcePath(SIP_FILE_ADD_AU_LINK_OK_NAME), zipPath);


        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;
        // use link sip
        final InputStream zipStream = new FileInputStream(new File(
            PropertiesUtils.getResourcePath(SIP_FILE_ADD_AU_LINK_OK_NAME_TARGET).toAbsolutePath() +
                "/" + zipName));

        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
            zipStream);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        // /////
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
        assertNotNull(processWorkflow.getSteps());
        try {
            Files.delete(new File(zipPath).toPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Test attach existing ObjectGroup to unit 1. Upload SIP 2. Get created GOT 3. Update manifest and set existing GOT
     * 4. Upload the new SIP
     *
     * @throws Exception
     */
    @RunWithCustomExecutor
    @Test
    public void testLinkUnitToExistingGOTOK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        // 1. First we create an AU by sip
        try {
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_FILE_OK_NAME);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }

        // 2. then we link another SIP to it
        String zipPath = null;
        String zipName = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 1) + ".zip";

        // prepare zip
        final MongoDatabase db = mongoClient.getDatabase("Vitam");
        MongoIterable<Document> resultUnits = db.getCollection("Unit").find();
        Document unit = resultUnits.first();
        String idGot = (String) unit.get("_og");
        replaceStringInFile(LINK_AU_TO_EXISTING_GOT_OK_NAME + "/manifest.xml",
            "(?<=<DataObjectGroupExistingReferenceId>).*?(?=</DataObjectGroupExistingReferenceId>)",
            idGot);

        zipPath = PropertiesUtils.getResourcePath(LINK_AU_TO_EXISTING_GOT_OK_NAME_TARGET).toAbsolutePath().toString() +
            "/" + zipName;
        zipFolder(PropertiesUtils.getResourcePath(LINK_AU_TO_EXISTING_GOT_OK_NAME), zipPath);


        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;
        // use link sip
        final InputStream zipStream = new FileInputStream(new File(
            PropertiesUtils.getResourcePath(LINK_AU_TO_EXISTING_GOT_OK_NAME_TARGET).toAbsolutePath() +
                "/" + zipName));

        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipStream);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;

        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow = processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        assertNotNull(processWorkflow.getSteps());

        // check got have to units
        assertEquals(db.getCollection("Unit").count(Filters.eq("_og", idGot)), 2);

        ArrayList<Document> logbookLifeCycleUnits =
            Lists.newArrayList(db.getCollection("LogbookLifeCycleUnit").find().iterator());

        List<Document> currentLogbookLifeCycleUnits =
            logbookLifeCycleUnits.stream().filter(t -> t.get("evIdProc").equals(containerName))
                .collect(Collectors.toList());

        List<Document> events = (List<Document>) Iterables.getOnlyElement(currentLogbookLifeCycleUnits).get("events");

        List<Document> lifeCycle = events.stream().filter(t -> t.get("outDetail").equals("LFC.CHECK_MANIFEST.OK"))
            .collect(Collectors.toList());
        assertThat(Iterables.getOnlyElement(lifeCycle).getString(EVENT_DETAILS)).containsIgnoringCase(idGot);
        try {
            Files.delete(new File(zipPath).toPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Test attach existing ObjectGroup to unit, but guid of the existing got is fake and really exists
     *
     * @throws Exception
     */
    @RunWithCustomExecutor
    @Test
    public void testLinkUnitToExistingGOTFakeGuidKO() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        String zipName = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 1) + ".zip";

        // prepare zip
        String idGot = "aecaaaaaachwwr22aaudeak5ouo22jyaaaaq";
        replaceStringInFile(LINK_AU_TO_EXISTING_GOT_OK_NAME + "/manifest.xml",
            "(?<=<DataObjectGroupExistingReferenceId>).*?(?=</DataObjectGroupExistingReferenceId>)",
            idGot);
        zipFolder(PropertiesUtils.getResourcePath(LINK_AU_TO_EXISTING_GOT_OK_NAME),
            PropertiesUtils.getResourcePath(LINK_AU_TO_EXISTING_GOT_OK_NAME_TARGET).toAbsolutePath().toString() +
                "/" + zipName);


        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;
        // use link sip
        final InputStream zipStream = new FileInputStream(new File(
            PropertiesUtils.getResourcePath(LINK_AU_TO_EXISTING_GOT_OK_NAME_TARGET).toAbsolutePath() +
                "/" + zipName));

        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipStream);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;

        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow = processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
        assertNotNull(processWorkflow.getSteps());
        try {
            Files.delete(new File(
                PropertiesUtils.getResourcePath(LINK_AU_TO_EXISTING_GOT_OK_NAME_TARGET).toAbsolutePath().toString() +
                    "/" + zipName).toPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void replaceStringInFile(String targetFilename, String textToReplace, String replacementText)
        throws IOException {
        Path path = PropertiesUtils.getResourcePath(targetFilename);
        Charset charset = StandardCharsets.UTF_8;

        String content = new String(Files.readAllBytes(path), charset);
        content = content.replaceAll(textToReplace, replacementText);
        Files.write(path, content.getBytes(charset));
    }


    private void zipFolder(final Path path, final String zipFilePath) throws IOException {
        try (
            FileOutputStream fos = new FileOutputStream(zipFilePath);
            ZipOutputStream zos = new ZipOutputStream(fos)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    zos.putNextEntry(new ZipEntry(path.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    zos.putNextEntry(new ZipEntry(path.relativize(dir).toString() + "/"));
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public void createLogbookOperation(GUID operationId, GUID objectId)
        throws LogbookClientBadRequestException, LogbookClientAlreadyExistsException, LogbookClientServerException,
        LogbookClientNotFoundException {
        createLogbookOperation(operationId, objectId, null, LogbookTypeProcess.INGEST);
    }

    public void createLogbookOperation(GUID operationId, GUID objectId, String type, LogbookTypeProcess typeProc)
        throws LogbookClientBadRequestException, LogbookClientAlreadyExistsException, LogbookClientServerException,
        LogbookClientNotFoundException {

        final LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
        if (type == null) {
            type = "Process_SIP_unitary";
        }
        final LogbookOperationParameters initParameters = LogbookParametersFactory.newLogbookOperationParameters(
            operationId, type, objectId,
            typeProc, StatusCode.STARTED,
            operationId != null ? operationId.toString() : "outcomeDetailMessage",
            operationId);
        logbookClient.create(initParameters);
    }


    @RunWithCustomExecutor
    @Test
    public void testBigWorkflow() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        // re-launch worker
        workerApplication.stop();
        // FIXME Sleep to be removed when asynchronous mode is implemented
        // Thread.sleep(8500);
        SystemPropertyUtil.set("jetty.worker.port", Integer.toString(PORT_SERVICE_WORKER));
        workerApplication = new WorkerMain(CONFIG_BIG_WORKER_PATH);
        workerApplication.start();
        try {
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);
            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_FILE_OK_NAME);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;
            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, BIG_WORFKLOW_NAME);

            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }

        workerApplication.stop();
        SystemPropertyUtil.set("jetty.worker.port", Integer.toString(PORT_SERVICE_WORKER));
        workerApplication = new WorkerMain(CONFIG_WORKER_PATH);
        workerApplication.start();
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowIncorrectManifestReference() throws Exception {

        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_MANIFEST_INCORRECT_REFERENCE);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;

        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
        assertNotNull(processWorkflow.getSteps());
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkerUnRegister() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);

            // 1. Stop the worker this will unregister the worker
            workerApplication.stop();
            Thread.sleep(500);

            // 2. Start the worker this will register the worker
            SystemPropertyUtil.set("jetty.worker.port", Integer.toString(PORT_SERVICE_WORKER));
            workerApplication = new WorkerMain(CONFIG_WORKER_PATH);
            workerApplication.start();
            Thread.sleep(500);

            // 3. Stop processing, this will make worker retry register
            processManagementApplication.stop();
            Thread.sleep(500);

            SystemPropertyUtil.set(ProcessManagementMain.PARAMETER_JETTY_SERVER_PORT,
                Integer.toString(PORT_SERVICE_PROCESSING));
            processManagementApplication = new ProcessManagementMain(CONFIG_PROCESSING_PATH);
            processManagementApplication.start();
            SystemPropertyUtil.clear(ProcessManagementMain.PARAMETER_JETTY_SERVER_PORT);
            // 4.Wait processing server start
            Thread.sleep(500);

            // For test, worker.conf is modified to have registerDelay: 1 (mean every one second worker try to register
            // it self

        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowBug2182() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_BUG_2182);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }


    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithSIP_KO_AU_ref_BDO() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;

        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_FILE_KO_AU_REF_BDO);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;
        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.KO, processWorkflow.getStatus());
    }

    @RunWithCustomExecutor
    @Test
    public void testPauseWorkflow() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_FILE_OK_NAME);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            ProcessDataManagement dataManagement = WorkspaceProcessDataManagement.getInstance();
            assertNotNull(dataManagement);

            assertNotNull(dataManagement.getProcessWorkflow(String.valueOf(ServerIdentity.getInstance().getServerId()),
                containerName));

            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.NEXT.getValue(),
                    containerName);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);

            assertNotNull(processWorkflow);
            assertEquals(ProcessState.PAUSE, processWorkflow.getState());
            assertEquals(StatusCode.OK, processWorkflow.getStatus());
            // Let the processing do the job
            ret = processingClient.updateOperationActionProcess(ProcessAction.NEXT.getValue(),
                containerName);

            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);

            assertNotNull(processWorkflow);
            assertEquals(ProcessState.PAUSE, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());


            ret = processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(),
                containerName);
            // Let the processing do the job
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);

            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());

            boolean exc = false;
            try {
                dataManagement.getProcessWorkflow(String.valueOf(ServerIdentity.getInstance().getServerId()),
                    containerName);
            } catch (ProcessingStorageWorkspaceException e) {
                exc = true;
            }

            // TODO the #2627 the workflow is not removed from workspace until 24h
            // assertTrue(exc);
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }


    @RunWithCustomExecutor
    @Test
    public void testWorkflowJsonValidationKOCA1() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_FILE_1791_CA1);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow = processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertThat(processWorkflow).isNotNull();
            assertThat(processWorkflow.getState()).isEqualTo(ProcessState.COMPLETED);
            assertThat(processWorkflow.getStatus()).isEqualTo(StatusCode.KO);

        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }


    @RunWithCustomExecutor
    @Test
    public void testWorkflowJsonValidationKOCA2() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_FILE_1791_CA2);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);


            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.KO, processWorkflow.getStatus());

        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowWithContractKO() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_REFERENCE_CONTRACT_KO);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.KO, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowSIPContractProdService() throws Exception {
        try {
            flush();

            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_FILE_OK_NAME);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
            assertNotNull(ret);

            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());

            LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
            fr.gouv.vitam.common.database.builder.request.single.Select selectQuery =
                new fr.gouv.vitam.common.database.builder.request.single.Select();
            selectQuery.setQuery(QueryHelper.eq("evIdProc", containerName));
            JsonNode logbookResult = logbookClient.selectOperation(selectQuery.getFinalSelect());

            assertEquals(logbookResult.get("$results").get(0).get("events").get(0).get("outDetail").asText(),
                "STP_INGEST_FINALISATION.OK");

        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowIngestBigTreeBugFix3062() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_ARBRE_3062);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.HOLDING_SCHEME.name(), containerName, INGEST_TREE_WORFKLOW);
            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
            assertNotNull(ret);

            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.OK, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }


    @RunWithCustomExecutor
    @Test
    public void testWorkflowOkSIP2GO() throws Exception {
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;

            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(OK_SIP_2_GO);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP, zipInputStreamSipObject);

            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);

            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());

            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }



    @RunWithCustomExecutor
    @Test
    public void testWorkflowRulesUpdate() throws Exception {        
        try {
            VitamThreadUtils.getVitamSession().setTenantId(tenantId);
            tryImportFile();

            final GUID operationGuid2 = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid2);
            final GUID objectGuid2 = GUIDFactory.newManifestGUID(tenantId);
            final String containerName2 = objectGuid2.getId();
            createLogbookOperation(operationGuid2, objectGuid2);

            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_COMPLEX_RULES);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName2);
            workspaceClient.uncompressObject(containerName2, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);

            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;
            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName2, WORFKLOW_NAME);
            final RequestResponse<JsonNode> ret2 =
                processingClient.executeOperationProcess(containerName2, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
            assertNotNull(ret2);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret2.getStatus());
            wait(containerName2);
            ProcessWorkflow processWorkflow2 =
                processMonitoring.findOneProcessWorkflow(containerName2, tenantId);
            assertNotNull(processWorkflow2);
            assertEquals(ProcessState.COMPLETED, processWorkflow2.getState());
            assertEquals(StatusCode.OK, processWorkflow2.getStatus());

            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // put rules into workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream rulesStream =
                PropertiesUtils.getResourceAsStream("integration-processing/RULES.json");
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.putObject(containerName,
                UpdateWorkflowConstants.PROCESSING_FOLDER + "/" + UpdateWorkflowConstants.UPDATED_RULES_JSON,
                rulesStream);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;
            processingClient.initVitamProcess(Contexts.UPDATE_RULES_ARCHIVE_UNITS.name(),
                containerName, UPD8_AU_WORKFLOW);
            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
            assertNotNull(ret);

            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());


            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.OK, processWorkflow.getStatus());

            final MongoDatabase db = mongoClient.getDatabase("Vitam");
            ArrayList<Document> logbookLifeCycleUnits =
                Lists.newArrayList(db.getCollection("LogbookLifeCycleUnit").find().iterator());

            List<Document> currentLogbookLifeCycleUnits =
                logbookLifeCycleUnits.stream().filter(t -> t.get("evIdProc").equals(containerName2))
                    .collect(Collectors.toList());
            currentLogbookLifeCycleUnits.forEach((lifecycle) -> {
                List<Document> events = (List<Document>) lifecycle.get("events");
                List<Document> lifecycleEvent =
                    events.stream().filter(t -> t.get("outDetail").equals("LFC.UPDATE_UNIT_RULES.OK"))
                        .collect(Collectors.toList());
                if (lifecycleEvent != null && lifecycleEvent.size() > 0) {
                    assertThat(Iterables.getOnlyElement(lifecycleEvent).getString(EVENT_DETAILS))
                        .containsIgnoringCase("diff");
                    assertThat(Iterables.getOnlyElement(lifecycleEvent).getString("outMessg")).isEqualTo(
                        "Succès de la mise à jour des règles de gestion de l'unité archivistique");
                }
            });

        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testWorkflowAddAttachementAndCheckRegister() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        // 1. First we create an AU by sip
        try {
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
            final String containerName = objectGuid.getId();
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject =
                PropertiesUtils.getResourceAsStream(SIP_PROD_SERV_A);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
            final RequestResponse<JsonNode> ret =
                processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                    Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());

            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow =
                processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            e.printStackTrace();
            fail("should not raized an exception");
        }

        String zipPath = null;
        // 2. then we link another SIP to it
        String zipName = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 1) + ".zip";

        // prepare zip
        final MongoDatabase db = mongoClient.getDatabase("Vitam");
        MongoIterable<Document> resultUnits = db.getCollection("Unit").find();
        Document unit = resultUnits.first();
        String idUnit = (String) unit.get("_id");
        replaceStringInFile(SIP_PROD_SERV_B_ATTACHED + "/manifest.xml", "(?<=<SystemId>).*?(?=</SystemId>)",
            idUnit);
        zipPath = PropertiesUtils.getResourcePath(SIP_FILE_ADD_AU_LINK_OK_NAME_TARGET).toAbsolutePath().toString() +
            "/" + zipName;
        zipFolder(PropertiesUtils.getResourcePath(SIP_PROD_SERV_B_ATTACHED), zipPath);


        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        RestAssured.port = PORT_SERVICE_WORKSPACE;
        RestAssured.basePath = WORKSPACE_PATH;
        // use link sip
        final InputStream zipStream = new FileInputStream(new File(
            PropertiesUtils.getResourcePath(SIP_FILE_ADD_AU_LINK_OK_NAME_TARGET).toAbsolutePath() +
                "/" + zipName));

        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
            zipStream);

        // call processing
        RestAssured.port = PORT_SERVICE_PROCESSING;
        RestAssured.basePath = PROCESSING_PATH;

        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName, WORFKLOW_NAME);
        final RequestResponse<JsonNode> ret =
            processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
                Contexts.DEFAULT_WORKFLOW.name(), ProcessAction.RESUME.getValue());
        assertNotNull(ret);
        assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow = processMonitoring.findOneProcessWorkflow(containerName, tenantId);
        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        assertNotNull(processWorkflow.getSteps());

        LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient();
        JsonNode logbookResult = logbookClient.selectOperationById(containerName,
            new fr.gouv.vitam.common.database.builder.request.single.Select().getFinalSelect());
        assertNotNull(logbookResult.get("$results").get(0));
        LogbookOperation logOperation =
            JsonHandler.getFromJsonNode(logbookResult.get("$results").get(0), LogbookOperation.class);
        List<LogbookEventOperation> events = logOperation.getEvents().stream()
            .filter(p -> (p.getEvType().equals("ACCESSION_REGISTRATION") && p.getOutcome().equals("OK")))
            .collect(Collectors.toList());
        events.forEach((event) -> {
            assertNotNull(event.getEvDetData());
            try {
                assertNotNull(JsonHandler.getFromString(event.getEvDetData()).get("Volumetry"));
            } catch (Exception e) {
                e.printStackTrace();
                fail("should not throws exception ");
            }
        });

        MongoIterable<Document> accessReg =
            db.getCollection("AccessionRegisterSummary").find(Filters.eq("OriginatingAgency", "P-A"));
        assertNotNull(accessReg);
        assertNotNull(accessReg.first());
        Document accessRegDoc = accessReg.first();
        // 2 units are attached - 1 was previously added
        assertEquals("2",
            ((Document) accessRegDoc.get("TotalUnits")).get(AccessionRegisterSummary.SYMBOLIC_REMAINED).toString());
        assertEquals("2",
            ((Document) accessRegDoc.get("TotalUnits")).get(AccessionRegisterSummary.ATTACHED).toString());
        assertEquals("1",
            ((Document) accessRegDoc.get("TotalUnits")).get(AccessionRegisterSummary.INGESTED).toString());
        assertEquals("1",
            ((Document) accessRegDoc.get("TotalUnits")).get(AccessionRegisterSummary.REMAINED).toString());

        // 1 object is attached - 1 was previously added
        assertEquals("1",
            ((Document) accessRegDoc.get("TotalObjects")).get(AccessionRegisterSummary.SYMBOLIC_REMAINED).toString());
        assertEquals("1",
            ((Document) accessRegDoc.get("TotalObjects")).get(AccessionRegisterSummary.ATTACHED).toString());
        assertEquals("1",
            ((Document) accessRegDoc.get("TotalObjects")).get(AccessionRegisterSummary.INGESTED).toString());

        // 1 Got is attached - 1 was previously added
        assertEquals("1", ((Document) accessRegDoc.get("TotalObjectGroups"))
            .get(AccessionRegisterSummary.SYMBOLIC_REMAINED).toString());
        assertEquals("1",
            ((Document) accessRegDoc.get("TotalObjectGroups")).get(AccessionRegisterSummary.ATTACHED).toString());
        assertEquals("1",
            ((Document) accessRegDoc.get("TotalObjectGroups")).get(AccessionRegisterSummary.INGESTED).toString());

        // 285804 octets is attached - 4109 was previously added
        assertEquals("285804",
            ((Document) accessRegDoc.get("ObjectSize")).get(AccessionRegisterSummary.SYMBOLIC_REMAINED).toString());
        assertEquals("285804",
            ((Document) accessRegDoc.get("ObjectSize")).get(AccessionRegisterSummary.ATTACHED).toString());
        assertEquals("4109",
            ((Document) accessRegDoc.get("ObjectSize")).get(AccessionRegisterSummary.INGESTED).toString());

        try {
            Files.delete(new File(zipPath).toPath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RunWithCustomExecutor
    @Test
    public void testBlankWorkflow() {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        try (MetaDataClient metaDataClient = MetaDataClientFactory.getInstance().getClient();
            AdminManagementClient functionalClient = AdminManagementClientFactory.getInstance().getClient()) {
            tryImportFile();
            final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
            VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
            createLogbookOperation(operationGuid, objectGuid);

            // workspace client dezip SIP in workspace
            RestAssured.port = PORT_SERVICE_WORKSPACE;
            RestAssured.basePath = WORKSPACE_PATH;
            final InputStream zipInputStreamSipObject = PropertiesUtils.getResourceAsStream(SIP_BUG_2721);
            workspaceClient = WorkspaceClientFactory.getInstance().getClient();
            workspaceClient.createContainer(containerName);
            workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
                zipInputStreamSipObject);
            // call processing
            RestAssured.port = PORT_SERVICE_PROCESSING;
            RestAssured.basePath = PROCESSING_PATH;

            metaDataClient.insertUnit(
                new InsertMultiQuery()
                    .addData((ObjectNode) JsonHandler
                        .getFromFile(PropertiesUtils.getResourceFile("integration-processing/unit_metadata.json")))
                    .getFinalInsert());

            metaDataClient.insertUnit(
                new InsertMultiQuery()
                    .addData(
                        (ObjectNode) JsonHandler.getFromFile(PropertiesUtils.getResourceFile(PROCESSING_UNIT_PLAN)))
                    .getFinalInsert());

            metaDataClient.flushUnits();
            // import contract
            File fileContracts = PropertiesUtils.getResourceFile(INGEST_CONTRACTS_PLAN);
            List<IngestContractModel> IngestContractModelList =
                JsonHandler.getFromFileAsTypeRefence(fileContracts, new TypeReference<List<IngestContractModel>>() {});

            functionalClient.importIngestContracts(IngestContractModelList);

            processingClient = ProcessingManagementClientFactory.getInstance().getClient();
            // Testing blank workflow
            processingClient.initVitamProcess(Contexts.BLANK_TEST.name(), containerName, BLANK_WORKFLOW_NAME);

            RequestResponse<ItemStatus> ret =
                processingClient.updateOperationActionProcess(ProcessAction.RESUME.getValue(), containerName);
            assertNotNull(ret);
            assertEquals(Status.ACCEPTED.getStatusCode(), ret.getStatus());

            wait(containerName);
            ProcessWorkflow processWorkflow = processMonitoring.findOneProcessWorkflow(containerName, tenantId);
            assertNotNull(processWorkflow);
            assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
            assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
        } catch (final Exception e) {
            LOGGER.error(e);
            fail("should not raized an exception" + e);
        }
    }


    @RunWithCustomExecutor
    @Test
    public void testValidateArchiveUnitSchemaBirthPlaceOK() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(tenantId);
        tryImportFile();
        final GUID operationGuid = GUIDFactory.newOperationLogbookGUID(tenantId);
        VitamThreadUtils.getVitamSession().setRequestId(operationGuid);
        final GUID objectGuid = GUIDFactory.newManifestGUID(tenantId);
        final String containerName = objectGuid.getId();
        createLogbookOperation(operationGuid, objectGuid);

        // workspace client dezip SIP in workspace
        final InputStream zipInputStreamSipObject =
            PropertiesUtils.getResourceAsStream(SIP_FILE_OK_BIRTH_PLACE);
        workspaceClient = WorkspaceClientFactory.getInstance().getClient();
        workspaceClient.createContainer(containerName);
        workspaceClient.uncompressObject(containerName, SIP_FOLDER, CommonMediaType.ZIP,
            zipInputStreamSipObject);

        processingClient = ProcessingManagementClientFactory.getInstance().getClient();
        processingClient.initVitamProcess(Contexts.DEFAULT_WORKFLOW.name(), containerName,
            WORFKLOW_NAME);
        // wait a little bit

        RequestResponse<JsonNode> resp = processingClient.executeOperationProcess(containerName, WORFKLOW_NAME,
            LogbookTypeProcess.INGEST.toString(), ProcessAction.RESUME.getValue());
        // wait a little bit
        assertNotNull(resp);

        assertEquals(Response.Status.ACCEPTED.getStatusCode(), resp.getStatus());

        wait(containerName);
        ProcessWorkflow processWorkflow =
            ProcessMonitoringImpl.getInstance().findOneProcessWorkflow(containerName, tenantId);

        assertNotNull(processWorkflow);
        assertEquals(ProcessState.COMPLETED, processWorkflow.getState());
        assertEquals(StatusCode.WARNING, processWorkflow.getStatus());
    }
}
