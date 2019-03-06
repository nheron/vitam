/**
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
 */
package fr.gouv.vitam.storage.offers.tape.impl.catalog;

import static fr.gouv.vitam.common.database.collections.VitamCollection.getMongoClientOptions;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import fr.gouv.vitam.common.database.server.mongodb.MongoDbAccess;
import fr.gouv.vitam.common.database.server.mongodb.SimpleMongoDBAccess;
import fr.gouv.vitam.common.database.server.query.QueryCriteria;
import fr.gouv.vitam.common.database.server.query.QueryCriteriaOperator;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.mongo.MongoRule;
import fr.gouv.vitam.storage.engine.common.collection.OfferCollections;
import fr.gouv.vitam.storage.engine.common.model.TapeCatalog;
import fr.gouv.vitam.storage.engine.common.model.TapeLocation;
import fr.gouv.vitam.storage.engine.common.model.TapeLocationType;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test @TapeCatalogRepository
 */
public class TapeCatalogRepositoryTest {

    public static final String TAPE_CATALOG_COLLECTION = OfferCollections.OFFER_TAPE_CATALOG.getName() + GUIDFactory.newGUID().getId();

    @ClassRule
    public static MongoRule mongoRule = new MongoRule(getMongoClientOptions(), TAPE_CATALOG_COLLECTION);

    private static TapeCatalogRepository tapeCatalogRepository;

    @BeforeClass
    public static void setUpBeforeClass() {
        MongoDbAccess mongoDbAccess = new SimpleMongoDBAccess(mongoRule.getMongoClient(), MongoRule.VITAM_DB);
        tapeCatalogRepository = new TapeCatalogRepository(mongoDbAccess.getMongoDatabase()
                .getCollection(TAPE_CATALOG_COLLECTION));
    }

    @AfterClass
    public static void setDownAfterClass() {
        mongoRule.handleAfterClass();
    }

    @Test
    public void shouldCreateTape() throws InvalidParseOperationException {
        // Given
        GUID id = GUIDFactory.newGUID();

        TapeCatalog tapeCatalog = getTapeModel(id);

        // When
        tapeCatalogRepository.createTape(tapeCatalog);

        // Then
        TapeCatalog tape = tapeCatalogRepository.findTapeById(id.toString());
        assertThat(tape).isNotNull();
        assertThat(tape.getCode()).isEqualTo("VIT0001");
        assertThat(tape.getLabel()).isEqualTo("VIT-TAPE-1");
        assertThat(tape.getCapacity()).isEqualTo(10000);
        assertThat(tape.getVersion()).isEqualTo(0);
    }

    @Test
    public void shouldFindTapeById() throws InvalidParseOperationException {
        // Given
        GUID id = GUIDFactory.newGUID();

        TapeCatalog tapeCatalog = getTapeModel(id);

        tapeCatalogRepository.createTape(tapeCatalog);

        // When
        TapeCatalog result = tapeCatalogRepository.findTapeById(tapeCatalog.getId());

        // Then
        assertThat(result.getId()).isEqualTo(id.toString());
    }

    @Test
    public void shouldFindTapesByCriteria() throws InvalidParseOperationException {
        // Given
        GUID id = GUIDFactory.newGUID();

        TapeCatalog tapeCatalog = getTapeModel(id);

        tapeCatalogRepository.createTape(tapeCatalog);

        // When
        List<QueryCriteria> criteria = new ArrayList<>();
        criteria.add(new QueryCriteria(TapeCatalog.CODE, tapeCatalog.getCode(), QueryCriteriaOperator.EQ));
        criteria.add(new QueryCriteria(TapeCatalog.CAPACITY, 10000L, QueryCriteriaOperator.GTE));
        List<TapeCatalog> result = tapeCatalogRepository.findTapes(criteria);

        // Then
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).getCapacity()).isEqualTo(tapeCatalog.getCapacity());
    }

    @Test
    public void shouldUpdateTape() throws InvalidParseOperationException {
        // Given
        GUID id = GUIDFactory.newGUID();

        TapeCatalog tapeCatalog = getTapeModel(id);

        tapeCatalogRepository.createTape(tapeCatalog);

        // When
        TapeCatalog currentTape = tapeCatalogRepository.findTapeById(tapeCatalog.getId());
        assertThat(currentTape.getCode()).isEqualTo("VIT0001");
        assertThat(currentTape.getVersion()).isEqualTo(0);
        assertThat(currentTape.getFileCount()).isEqualTo(200L);

        Map<String, Object> updates = Maps.newHashMap();
        updates.put(TapeCatalog.CODE, "FakeCode");
        updates.put(TapeCatalog.FILE_COUNT, 201);
        tapeCatalogRepository.updateTape(id.toString(), updates);

        // Then
        TapeCatalog updatedTape = tapeCatalogRepository.findTapeById(tapeCatalog.getId());
        assertThat(updatedTape.getCode()).isEqualTo("FakeCode");
        assertThat(updatedTape.getVersion()).isEqualTo(1);
        assertThat(updatedTape.getFileCount()).isEqualTo(201L);
    }

    @Test
    public void shouldReplaceTape() throws InvalidParseOperationException {
        // Given
        GUID id = GUIDFactory.newGUID();

        TapeCatalog tapeCatalog = getTapeModel(id);

        tapeCatalogRepository.createTape(tapeCatalog);

        // When
        TapeCatalog currentTape = tapeCatalogRepository.findTapeById(tapeCatalog.getId());
        assertThat(currentTape.getCode()).isEqualTo("VIT0001");
        assertThat(currentTape.getVersion()).isEqualTo(0);

        currentTape.setCode("FakeCode");
        tapeCatalogRepository.replaceTape(currentTape);

        // Then
        TapeCatalog updatedTape = tapeCatalogRepository.findTapeById(tapeCatalog.getId());
        assertThat(updatedTape.getCode()).isEqualTo("FakeCode");
        assertThat(updatedTape.getVersion()).isEqualTo(1);
    }

    private TapeCatalog getTapeModel(GUID id) {
        TapeCatalog tapeCatalog = new TapeCatalog();
        tapeCatalog.setId(id.toString());
        tapeCatalog.setCapacity(10000L);
        tapeCatalog.setRemainingSize(5000L);
        tapeCatalog.setFileCount(200L);
        tapeCatalog.setCode("VIT0001");
        tapeCatalog.setLabel("VIT-TAPE-1");
        tapeCatalog.setLibrary("VIT-LIB-1");
        tapeCatalog.setType("LTO-6");
        tapeCatalog.setCompressed(false);
        tapeCatalog.setWorm(false);
        tapeCatalog.setCurrentLocation(new TapeLocation(1, TapeLocationType.DIRVE));
        tapeCatalog.setPreviousLocation(new TapeLocation(2, TapeLocationType.SLOT));
        return tapeCatalog;
    }

}
