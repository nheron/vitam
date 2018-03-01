/**
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 * <p>
 * contact.vitam@culture.gouv.fr
 * <p>
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 * <p>
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 * <p>
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 * <p>
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 * <p>
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.common.model.administration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.gouv.vitam.common.model.ModelConstants;

/**
 * POJO java use for mapping @{@link fr.gouv.vitam.functional.administration.common.ArchiveUnitProfile}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ArchiveUnitProfileModel {

    public static final String TAG_IDENTIFIER = "Identifier";
    public static final String TAG_NAME = "Name";
    public static final String TAG_DESCRIPTION = "Description";
    public static final String TAG_STATUS = "Status";
    public static final String CREATION_DATE = "CreationDate";
    public static final String LAST_UPDATE = "LastUpdate";
    public static final String ACTIVATION_DATE = "ActivationDate";
    public static final String DEACTIVATION_DATE = "DeactivationDate";
    
    /**
     * unique id
     */
    @JsonProperty(ModelConstants.HASH + ModelConstants.TAG_ID)
    private String id;

    /**
     * tenant id
     */
    @JsonProperty(ModelConstants.HASH + ModelConstants.TAG_TENANT)
    private Integer tenant;

    /**
     * document version
     */
    @JsonProperty(ModelConstants.HASH + ModelConstants.TAG_VERSION)
    private Integer version;

    @JsonProperty(TAG_IDENTIFIER)
    private String identifier;

    @JsonProperty(TAG_NAME)
    private String name;

    @JsonProperty(TAG_DESCRIPTION)
    private String description;

    @JsonProperty(TAG_STATUS)
    private ArchiveUnitProfileStatus status;

    @JsonProperty(CREATION_DATE)
    private String creationdate;

    @JsonProperty(LAST_UPDATE)
    private String lastupdate;

    @JsonProperty(ACTIVATION_DATE)
    private String activationdate;

    @JsonProperty(DEACTIVATION_DATE)
    private String deactivationdate;
    
    /**
     * Constructor without fields use for jackson
     */
    public ArchiveUnitProfileModel() {
        super();
    }

    public String getId() {
        return id;
    }

    public ArchiveUnitProfileModel setId(String id) {
        this.id = id;
        return this;
    }

    public Integer getTenant() {
        return tenant;
    }

    public ArchiveUnitProfileModel setTenant(Integer tenant) {
        this.tenant = tenant;
        return this;
    }

    public Integer getVersion() {
        return version;
    }

    public ArchiveUnitProfileModel setVersion(Integer version) {
        this.version = version;
        return this;
    }

    public String getIdentifier() {
        return identifier;
    }

    public ArchiveUnitProfileModel setIdentifier(String identifier) {
        this.identifier = identifier;
        return this;
    }

    public String getName() {
        return name;
    }

    public ArchiveUnitProfileModel setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ArchiveUnitProfileModel setDescription(String description) {
        this.description = description;
        return this;
    }

    public ArchiveUnitProfileStatus getStatus() {
        return status;
    }

    public ArchiveUnitProfileModel setStatus(ArchiveUnitProfileStatus status) {
        this.status = status;
        return this;
    }

    public String getCreationdate() {
        return creationdate;
    }

    public ArchiveUnitProfileModel setCreationdate(String creationdate) {
        this.creationdate = creationdate;
        return this;
    }

    public String getLastupdate() {
        return lastupdate;
    }

    public ArchiveUnitProfileModel setLastupdate(String lastupdate) {
        this.lastupdate = lastupdate;
        return this;
    }

    public String getActivationdate() {
        return activationdate;
    }

    public ArchiveUnitProfileModel setActivationdate(String activationdate) {
        this.activationdate = activationdate;
        return this;
    }

    public String getDeactivationdate() {
        return deactivationdate;
    }

    public ArchiveUnitProfileModel setDeactivationdate(String deactivationdate) {
        this.deactivationdate = deactivationdate;
        return this;
    }
}
