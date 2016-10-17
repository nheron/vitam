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

package fr.gouv.vitam.access.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import fr.gouv.vitam.access.common.exception.AccessClientNotFoundException;
import fr.gouv.vitam.access.common.exception.AccessClientServerException;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.stream.StreamUtils;

/**
 * Access client
 *
 * TODO : tenantId should be determined otherwise with a config or so
 */
public class AccessClientRest implements AccessClient {
    private static final String RESOURCE_PATH = "/access/v1";
    private static final String BLANK_DSL = "select DSL is blank";
    private static final String BLANK_UNIT_ID = "unit identifier should be filled";
    private static final String BLANK_OBJECT_ID = "object identifier should be filled";
    private static final String BLANK_OBJECT_GROUP_ID = "object identifier should be filled";
    private static final String BLANK_USAGE = "usage should be filled";
    private static final String BLANK_VERSION = "usage version should be filled";

    private final String serviceUrl;
    private final Client client;

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AccessClientRest.class);

    private static final int TENANT_ID = 0;

    /**
     * @param server - localhost
     * @param port - define 8082
     */
    public AccessClientRest(String server, int port) {
        serviceUrl = "http://" + server + ":" + port + RESOURCE_PATH;
        final ClientConfig config = new ClientConfig();
        config.register(JacksonJsonProvider.class);
        config.register(JacksonFeature.class);
        client = ClientBuilder.newClient(config);
    }

    /**
     * @return : status of access server 200 : server is alive
     */
    public Response status() {
        return client.target(serviceUrl).path("status").request().get();
    }

    @Override
    public JsonNode selectUnits(String selectQuery)
        throws InvalidParseOperationException, AccessClientServerException, AccessClientNotFoundException {
        if (StringUtils.isBlank(selectQuery)) {
            throw new IllegalArgumentException("select DSL is blank");
        }
        // TODO : Request ID should be generated by the caller code, not the client directly
        final GUID guid = GUIDFactory.newRequestIdGUID(TENANT_ID);

        final Response response = client.target(serviceUrl).path("units").request(MediaType.APPLICATION_JSON)
            .header(GlobalDataRest.X_HTTP_METHOD_OVERRIDE, "GET")
            .header(GlobalDataRest.X_REQUEST_ID, guid.toString()).accept(MediaType.APPLICATION_JSON)
            .post(Entity.entity(selectQuery, MediaType.APPLICATION_JSON), Response.class);

        if (response.getStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            throw new AccessClientServerException("Internal Server Error"); // access-common
        } else if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) { // access-common
            throw new AccessClientNotFoundException("Not Found Exception");
        } else if (response.getStatus() == Status.BAD_REQUEST.getStatusCode()) {
            throw new InvalidParseOperationException("Invalid Parse Operation");// common
        }

        return response.readEntity(JsonNode.class);
    }

    @Override
    public JsonNode selectUnitbyId(String selectQuery, String id_unit)
        throws InvalidParseOperationException, AccessClientServerException, AccessClientNotFoundException {
        if (StringUtils.isBlank(selectQuery)) {
            throw new IllegalArgumentException(BLANK_DSL);
        }
        if (StringUtils.isEmpty(id_unit)) {
            throw new IllegalArgumentException(BLANK_UNIT_ID);
        }

        // TODO : Request ID should be generated by the caller code, not the client directly
        final GUID guid = GUIDFactory.newRequestIdGUID(TENANT_ID);

        final Response response =
            client.target(serviceUrl).path("units/" + id_unit).request(MediaType.APPLICATION_JSON)
                .header(GlobalDataRest.X_HTTP_METHOD_OVERRIDE, "GET")
                .header(GlobalDataRest.X_REQUEST_ID, guid.toString())
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(selectQuery, MediaType.APPLICATION_JSON), Response.class);

        if (response.getStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            throw new AccessClientServerException("Internal Server Error"); // access-common
        } else if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) { // access-common
            throw new AccessClientNotFoundException("Not Found Exception");
        } else if (response.getStatus() == Status.BAD_REQUEST.getStatusCode()) {
            throw new InvalidParseOperationException("Invalid Parse Operation");// common
        }

        return response.readEntity(JsonNode.class);
    }

    @Override
    public JsonNode updateUnitbyId(String updateQuery, String unitId)
        throws InvalidParseOperationException, AccessClientServerException, AccessClientNotFoundException {
        if (StringUtils.isBlank(updateQuery)) {
            throw new IllegalArgumentException(BLANK_DSL);
        }
        if (StringUtils.isEmpty(unitId)) {
            throw new IllegalArgumentException(BLANK_UNIT_ID);
        }

        // TODO : Request ID should be generated by the caller code, not the client directly
        final GUID guid = GUIDFactory.newRequestIdGUID(TENANT_ID);

        final Response response =
            client.target(serviceUrl).path("units/" + unitId).request(MediaType.APPLICATION_JSON)
                .header(GlobalDataRest.X_REQUEST_ID, guid.toString())
                .accept(MediaType.APPLICATION_JSON)
                .put(Entity.entity(updateQuery, MediaType.APPLICATION_JSON), Response.class);

        if (response.getStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            throw new AccessClientServerException("Internal Server Error"); // access-common
        } else if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) { // access-common
            throw new AccessClientNotFoundException("Not Found Exception");
        } else if (response.getStatus() == Status.BAD_REQUEST.getStatusCode()) {
            throw new InvalidParseOperationException("Invalid Parse Operation");// common
        }

        return response.readEntity(JsonNode.class);
    }

    @Override
    public JsonNode selectObjectbyId(String selectObjectQuery, String objectId)
        throws InvalidParseOperationException, AccessClientServerException, AccessClientNotFoundException {
        ParametersChecker.checkParameter(BLANK_DSL, selectObjectQuery);
        ParametersChecker.checkParameter(BLANK_OBJECT_ID, objectId);

        // TODO : Request ID should be generated by the caller code, not the client directly
        final GUID guid = GUIDFactory.newRequestIdGUID(TENANT_ID);

        Response response = null;
        try {
            response =
                client.target(serviceUrl).path("objects/" + objectId).request(MediaType.APPLICATION_JSON)
                    .header(GlobalDataRest.X_HTTP_METHOD_OVERRIDE, "GET")
                    .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
                    .header(GlobalDataRest.X_REQUEST_ID, guid.toString())
                    .accept(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(selectObjectQuery, MediaType.APPLICATION_JSON), Response.class);
            final Response.Status status = Response.Status.fromStatusCode(response.getStatus());
            if (response.getStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
                LOGGER.error("Internal Server Error" + " : " + status.getReasonPhrase());
                throw new AccessClientServerException("Internal Server Error");
            } else if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new AccessClientNotFoundException(status.getReasonPhrase());
            } else if (response.getStatus() == Status.BAD_REQUEST.getStatusCode()) {
                throw new InvalidParseOperationException("Invalid Parse Operation");
            } else if (response.getStatus() == Status.PRECONDITION_FAILED.getStatusCode()) {
                throw new AccessClientServerException(response.getStatusInfo().getReasonPhrase());
            }

            return response.readEntity(JsonNode.class);

        } finally {
            Optional.ofNullable(response).ifPresent(Response::close);
        }

    }

    @Override
    public InputStream getObjectAsInputStream(String selectObjectQuery, String objectGroupId, String usage, int version)
        throws InvalidParseOperationException, AccessClientServerException, AccessClientNotFoundException {
        ParametersChecker.checkParameter(BLANK_DSL, selectObjectQuery);
        ParametersChecker.checkParameter(BLANK_OBJECT_GROUP_ID, objectGroupId);
        ParametersChecker.checkParameter(BLANK_USAGE, usage);
        ParametersChecker.checkParameter(BLANK_VERSION, version);

        // TODO : Request ID should be generated by the caller code, not the client directly
        final GUID guid = GUIDFactory.newRequestIdGUID(TENANT_ID);
        InputStream stream;
        Response response = null;
        try {
            response =
                client.target(serviceUrl).path("objects/" + objectGroupId).request(MediaType.APPLICATION_OCTET_STREAM)
                    .header(GlobalDataRest.X_HTTP_METHOD_OVERRIDE, "GET")
                    .header(GlobalDataRest.X_TENANT_ID, TENANT_ID)
                    .header(GlobalDataRest.X_REQUEST_ID, guid.toString())
                    .header(GlobalDataRest.X_QUALIFIER, usage)
                    .header(GlobalDataRest.X_VERSION, version)
                    .post(Entity.entity(selectObjectQuery, MediaType.APPLICATION_JSON), Response.class);
            final Response.Status status = Response.Status.fromStatusCode(response.getStatus());
            if (response.getStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
                LOGGER.error("Internal Server Error" + " : " + status.getReasonPhrase());
                throw new AccessClientServerException("Internal Server Error");
            } else if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new AccessClientNotFoundException(status.getReasonPhrase());
            } else if (response.getStatus() == Status.BAD_REQUEST.getStatusCode()) {
                throw new InvalidParseOperationException("Invalid Parse Operation");
            } else if (response.getStatus() == Status.PRECONDITION_FAILED.getStatusCode()) {
                throw new AccessClientServerException(response.getStatusInfo().getReasonPhrase());
            }

            try (final InputStream streamClosedAutomatically = response.readEntity(InputStream.class)) {
                // TODO : this is ugly but necessarily in order to close the response and avoid concurrent issues
                // to be improved (https://jersey.java.net/documentation/latest/client.html#d0e5170) and
                // remove the IOUtils.toByteArray after correction of problem
                stream = new ByteArrayInputStream(IOUtils.toByteArray(streamClosedAutomatically));
            } catch (final IOException e) {
                LOGGER.error(e);
                throw new AccessClientServerException("Internal Server Error");
            }
            return stream;
        } finally {
            Optional.ofNullable(response).ifPresent(Response::close);
        }
    }

}
