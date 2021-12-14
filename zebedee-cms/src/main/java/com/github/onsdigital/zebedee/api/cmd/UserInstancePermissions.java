package com.github.onsdigital.zebedee.api.cmd;

import com.github.davidcarboni.restolino.framework.Api;
import com.github.onsdigital.zebedee.permissions.cmd.CMDPermissionsService;
import com.github.onsdigital.zebedee.permissions.cmd.CMDPermissionsServiceImpl;
import com.github.onsdigital.zebedee.permissions.cmd.CRUD;
import com.github.onsdigital.zebedee.permissions.cmd.GetPermissionsRequest;
import com.github.onsdigital.zebedee.permissions.cmd.PermissionsException;
import com.github.onsdigital.zebedee.util.HttpResponseWriter;

import static com.github.onsdigital.zebedee.permissions.cmd.PermissionsException.internalServerErrorException;
import static com.github.onsdigital.zebedee.permissions.cmd.PermissionsException.sessionIDNotProvidedException;
import static com.github.onsdigital.zebedee.util.JsonUtils.writeResponseEntity;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * API endpoint for getting a user's CMD instance permissions.
 *
 * @deprecated the permissions APIs are deprecated in favour of the new dp-permissions-api. Once the migration of all
 *             dataset services to the new dp-authorisation v2 library has been completed these endpoints should be
 *             removed.
 */
@Deprecated
@Api
public class UserInstancePermissions extends PermissionsAPIBase {

    /**
     * Create a new UserInstancePermissions endpoint using the default constructor parameters.
     */
    public UserInstancePermissions() {
        super(CMDPermissionsServiceImpl.getInstance(), (r, b, s) -> writeResponseEntity(r, b, s));
    }

    /**
     * Construct a new UserInstancePermissions endpoint.
     *
     * @param cmdPermissionsService
     * @param responseWriter        the http response writer impl to use.
     */
    public UserInstancePermissions(CMDPermissionsService cmdPermissionsService, HttpResponseWriter responseWriter) {
        super(cmdPermissionsService, responseWriter);
    }

    @Override
    public CRUD getPermissions(GetPermissionsRequest request) throws PermissionsException {
        validateRequest(request);
        return permissionsService.getUserInstancePermissions(request);
    }

    void validateRequest(GetPermissionsRequest request) throws PermissionsException {
        if (request == null) {
            throw internalServerErrorException();
        }

        if (isEmpty(request.getSessionID())) {
            throw sessionIDNotProvidedException();
        }
    }
}
