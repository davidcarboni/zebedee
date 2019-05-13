package com.github.onsdigital.zebedee.authorisation;

import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.Collections;
import com.github.onsdigital.zebedee.service.ServiceSupplier;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.session.service.SessionsService;
import com.github.onsdigital.zebedee.user.service.UsersService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class AuthorisationServiceImplTest {

    private static final String SESSION_ID = "666";

    @Mock
    private SessionsService sessionsService;

    @Mock
    private UsersService usersService;

    @Mock
    private Collections collections;

    @Mock
    private Collection collection;

    private CollectionDescription description;

    private ServiceSupplier<SessionsService> sessionServiceSupplier = () -> sessionsService;
    private ServiceSupplier<UsersService> userServiceSupplier = () -> usersService;
    private ServiceSupplier<Collections> collectionsSupplier = () -> collections;

    private AuthorisationService service;
    private UserIdentityException notAuthenticatedEx;
    private UserIdentityException internalServerErrorEx;
    private UserIdentityException notFoundEx;
    private Session session;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        notAuthenticatedEx = new UserIdentityException("user not authenticated", SC_UNAUTHORIZED);
        internalServerErrorEx = new UserIdentityException("internal server error", SC_INTERNAL_SERVER_ERROR);
        notFoundEx = new UserIdentityException("user does not exist", SC_NOT_FOUND);

        service = new AuthorisationServiceImpl();

        session = new Session();
        session.setEmail("rickSanchez@CitadelOfRicks.com");
        session.setId(SESSION_ID);

        ReflectionTestUtils.setField(service, "sessionServiceSupplier", sessionServiceSupplier);
        ReflectionTestUtils.setField(service, "userServiceSupplier", userServiceSupplier);
        ReflectionTestUtils.setField(service, "collectionsSupplier", collectionsSupplier);
    }

    @Test(expected = UserIdentityException.class)
    public void shouldReturnNotAuthorisedIfSessionIDNull() throws Exception {
        try {
            service.identifyUser(null);
        } catch (UserIdentityException ex) {
            assertThat(ex, equalTo(notAuthenticatedEx));
            verifyZeroInteractions(sessionsService, usersService);
            throw ex;
        }
    }

    @Test(expected = UserIdentityException.class)
    public void shouldReturnNotAuthorisedIfSessionIDEmpty() throws Exception {
        try {
            service.identifyUser("");
        } catch (UserIdentityException ex) {
            assertThat(ex, equalTo(notAuthenticatedEx));
            verifyZeroInteractions(sessionsService, usersService);
            throw ex;
        }
    }

    @Test(expected = UserIdentityException.class)
    public void shouldReturnInternalServerErrorIfErrorGettingSession() throws Exception {
        when(sessionsService.get(SESSION_ID))
                .thenThrow(new IOException("error getting session"));
        try {
            service.identifyUser(SESSION_ID);
        } catch (UserIdentityException ex) {
            assertThat(ex, equalTo(internalServerErrorEx));
            verify(sessionsService, times(1)).get(SESSION_ID);
            verifyZeroInteractions(usersService);
            throw ex;
        }
    }

    @Test(expected = UserIdentityException.class)
    public void shouldReturnNotAuthenicatedIfSessionNotFound() throws Exception {
        when(sessionsService.get(SESSION_ID))
                .thenReturn(null);
        try {
            service.identifyUser(SESSION_ID);
        } catch (UserIdentityException ex) {
            assertThat(ex, equalTo(notAuthenticatedEx));
            verify(sessionsService, times(1)).get(SESSION_ID);
            verifyZeroInteractions(usersService);
            throw ex;
        }
    }

    @Test(expected = UserIdentityException.class)
    public void shouldReturnNotFoundIfUserDoesNotExist() throws Exception {
        when(sessionsService.get(SESSION_ID))
                .thenReturn(session);
        when(usersService.exists(session.getEmail()))
                .thenReturn(false);
        try {
            service.identifyUser(SESSION_ID);
        } catch (UserIdentityException ex) {
            assertThat(ex, equalTo(notFoundEx));
            verify(sessionsService, times(1)).get(SESSION_ID);
            verify(usersService, times(1)).exists(session.getEmail());
            throw ex;
        }
    }

    @Test(expected = UserIdentityException.class)
    public void shouldReturnInternalServerErrorIfErrorCheckingUserExistance() throws Exception {
        when(sessionsService.get(SESSION_ID))
                .thenReturn(session);
        when(usersService.exists(session.getEmail()))
                .thenThrow(new IOException("something terrible happened!"));
        try {
            service.identifyUser(SESSION_ID);
        } catch (UserIdentityException ex) {
            assertThat(ex, equalTo(internalServerErrorEx));
            verify(sessionsService, times(1)).get(SESSION_ID);
            verify(usersService, times(1)).exists(session.getEmail());
            throw ex;
        }
    }

    @Test
    public void shouldReturnUserIdentityIfSessionAndUserExist() throws Exception {
        when(sessionsService.get(SESSION_ID))
                .thenReturn(session);
        when(usersService.exists(session.getEmail()))
                .thenReturn(true);

        UserIdentity expected = new UserIdentity(session);

        UserIdentity actual = service.identifyUser(SESSION_ID);

        assertThat(actual, equalTo(expected));
        verify(sessionsService, times(1)).get(SESSION_ID);
        verify(usersService, times(1)).exists(session.getEmail());
    }


    @Test(expected = DatasetPermissionsException.class)
    public void testGetSession_sessionIDNull() throws Exception {
        AuthorisationServiceImpl serviceImpl = (AuthorisationServiceImpl) service;

        try {
            serviceImpl.getSession(null);
        } catch (DatasetPermissionsException ex) {
            verify(sessionsService, never()).get(anyString());

            assertThat(ex.statusCode, equalTo(SC_BAD_REQUEST));
            assertThat(ex.getMessage(), equalTo("session ID required but empty"));
            throw ex;
        }
    }

    @Test(expected = DatasetPermissionsException.class)
    public void testGetSession_sessionIDEmpty() throws Exception {
        AuthorisationServiceImpl serviceImpl = (AuthorisationServiceImpl) service;

        try {
            serviceImpl.getSession("");
        } catch (DatasetPermissionsException ex) {
            verify(sessionsService, never()).get(anyString());

            assertThat(ex.statusCode, equalTo(SC_BAD_REQUEST));
            assertThat(ex.getMessage(), equalTo("session ID required but empty"));
            throw ex;
        }
    }

    @Test(expected = DatasetPermissionsException.class)
    public void testGetSession_IOException() throws Exception {
        AuthorisationServiceImpl serviceImpl = (AuthorisationServiceImpl) service;

        when(sessionsService.get("666"))
                .thenThrow(new IOException("BOOOOOOOOM"));

        try {
            serviceImpl.getSession("666");
        } catch (DatasetPermissionsException ex) {
            verify(sessionsService, times(1)).get("666");

            assertThat(ex.statusCode, equalTo(SC_INTERNAL_SERVER_ERROR));
            assertThat(ex.getMessage(), equalTo("internal server error"));
            throw ex;
        }
    }

    @Test(expected = DatasetPermissionsException.class)
    public void testGetSession_sessionNotFound() throws Exception {
        AuthorisationServiceImpl serviceImpl = (AuthorisationServiceImpl) service;

        when(sessionsService.get("666"))
                .thenReturn(null);

        try {
            serviceImpl.getSession("666");
        } catch (DatasetPermissionsException ex) {
            verify(sessionsService, times(1)).get("666");

            assertThat(ex.statusCode, equalTo(SC_UNAUTHORIZED));
            assertThat(ex.getMessage(), equalTo("session not found"));
            throw ex;
        }
    }

    @Test(expected = DatasetPermissionsException.class)
    public void testGetSession_sessionExpired() throws Exception {
        AuthorisationServiceImpl serviceImpl = (AuthorisationServiceImpl) service;

        when(sessionsService.get("666"))
                .thenReturn(session);
        when(sessionsService.expired(session))
                .thenReturn(true);

        try {
            serviceImpl.getSession("666");
        } catch (DatasetPermissionsException ex) {
            verify(sessionsService, times(1)).get("666");
            verify(sessionsService, times(1)).expired(session);

            assertThat(ex.statusCode, equalTo(SC_UNAUTHORIZED));
            assertThat(ex.getMessage(), equalTo("session expired"));
            throw ex;
        }
    }


}
