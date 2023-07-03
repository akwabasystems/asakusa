
package com.akwabasystems.asakusa.rest;

import com.akwabasystems.asakusa.model.Gender;
import com.akwabasystems.asakusa.model.User;
import com.akwabasystems.asakusa.model.UserPreferences;
import com.akwabasystems.asakusa.rest.utils.UserResponse;
import com.akwabasystems.asakusa.rest.service.UserService;
import com.akwabasystems.asakusa.rest.utils.ApplicationError;
import com.akwabasystems.asakusa.rest.utils.AuthorizationTicket;
import com.akwabasystems.asakusa.rest.utils.QueryParameter;
import com.akwabasystems.asakusa.rest.utils.QueryUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.java.Log;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v3/users")
@Log
public class UserController extends BaseController {

    @Autowired
    private UserService userService;
    
    
    /**
     * Handles a request to create a user account
     * 
     * @param request       the incoming request
     * @param response      the outgoing response
     * @param map           an object that contains the query parameters
     * @return a JSON object with the details of the newly created user
     * @throws Exception if the request fails
     */
    @PostMapping("/")
    public ResponseEntity<?> createAccount(HttpServletRequest request,
                                           HttpServletResponse response,
                                           @RequestBody LinkedHashMap<String,Object> map) 
                                                         throws Exception {
        String appId = (String) request.getHeader(QueryParameter.ACCESS_TOKEN);
        
        Map<String,Object> parameterMap = new LinkedHashMap<>();
        
        String userId = (String) QueryUtils.getValueRequired(map, QueryParameter.USER_ID);
        String email = (String) QueryUtils.getValueRequired(map, QueryParameter.EMAIL);
        String firstName = (String)QueryUtils.getValueRequired(map, QueryParameter.FIRST_NAME);
        String lastName = (String)QueryUtils.getValueRequired(map, QueryParameter.LAST_NAME);
        String password = (String)QueryUtils.getValueRequired(map, QueryParameter.PASSWORD);
        String locale = (String) QueryUtils.getValueWithDefault(map, QueryParameter.LOCALE, "en");
        String gender = (String) QueryUtils.getValueWithDefault(map, QueryParameter.GENDER, "FEMALE");
        
        parameterMap.put(QueryParameter.USER_ID, userId);
        parameterMap.put(QueryParameter.FIRST_NAME, StringEscapeUtils.escapeJava(firstName));
        parameterMap.put(QueryParameter.LAST_NAME, StringEscapeUtils.escapeJava(lastName));
        parameterMap.put(QueryParameter.EMAIL, email);
        parameterMap.put(QueryParameter.PASSWORD, password);
        parameterMap.put(QueryParameter.LOCALE, locale);
        parameterMap.put(QueryParameter.GENDER, gender);
        
        QueryUtils.populateMapIfPresent(map, parameterMap, QueryParameter.PHONE_NUMBER);
        QueryUtils.populateMapIfPresent(map, parameterMap, QueryParameter.CLIENT);
        
        /** 
         * For security purposes when creating an account, the "appId" is substituted for the "userId" in the
         * "accessToken" header
         */
        boolean created = userService.createAccount(getAuthorizationTicket(appId), parameterMap);
        
        if (created) {
            String userInfoURI = String.format("/api/v3/users/%s", userId);
            UserResponse userResponse = new UserResponse(userId, firstName, lastName);
            userResponse.setEmail(email);
            userResponse.setUsername(userId);
            userResponse.setGender(Gender.fromString(gender));
            userResponse.setLocale(locale);
        
            /** Return an HTTP 201 (Created) response with the user details */
            return ResponseEntity.created(new URI(userInfoURI)).body(userResponse);
            
        } else {
            log.severe(String.format("[UserController#createAccount] - Error creating account for user %s", userId));
            
            ProblemDetail details = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            details.setTitle(ApplicationError.HTTP_ERROR);
            details.setInstance(new URI(request.getRequestURI()));
            return ResponseEntity.of(details).build();
        }

    }
    
    
    /**
     * Handles a request to retrieve the details for a given user
     * 
     * @param request       the incoming request
     * @param response      the outgoing response
     * @param id            the ID of the user for whom to retrieve the details
     * @return a JSON object with the user's details
     * @throws Exception if the request fails
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> userInfo(HttpServletRequest request,
                                                 HttpServletResponse response,
                                                 @PathVariable String id) 
                                                      throws Exception {
        String accessToken = (String) request.getHeader(QueryParameter.ACCESS_TOKEN);
        User user = userService.findUserById(getAuthorizationTicket(id, accessToken));
        
        if (user == null) {
            ProblemDetail details = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            details.setTitle(ApplicationError.USER_NOT_FOUND);
            details.setInstance(new URI(request.getRequestURI()));
            return ResponseEntity.of(details).build();
        } else {
            return ResponseEntity.ok(UserResponse.fromUser(user));
        }
    }
    
    
    /**
     * Handles a request to update a user account
     * 
     * @param request       the incoming request
     * @param response      the outgoing response
     * @param map           an object that contains the query parameters
     * @return a JSON object with the updated user details
     * @throws Exception if the request fails
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(HttpServletRequest request,
                                        HttpServletResponse response,
                                        @PathVariable String id,
                                        @RequestBody LinkedHashMap<String,Object> map) 
                                                         throws Exception {
        String accessToken = (String) request.getHeader(QueryParameter.ACCESS_TOKEN);

        String firstName = (String)QueryUtils.getValueRequired(map, QueryParameter.FIRST_NAME);
        String lastName = (String)QueryUtils.getValueRequired(map, QueryParameter.LAST_NAME);
        String locale = (String) QueryUtils.getValueWithDefault(map, QueryParameter.LOCALE, "en");
        String gender = (String) QueryUtils.getValueWithDefault(map, QueryParameter.GENDER, "FEMALE");
        
        Map<String,Object> parameterMap = new HashMap<>();
        parameterMap.put(QueryParameter.FIRST_NAME, firstName);
        parameterMap.put(QueryParameter.LAST_NAME, lastName);
        parameterMap.put(QueryParameter.LOCALE, locale);
        parameterMap.put(QueryParameter.GENDER, gender);
        
        AuthorizationTicket authTicket = getAuthorizationTicket(id, accessToken);
        boolean updated = userService.updateAccount(authTicket, parameterMap);

        if (updated) {
            User updatedUser = userService.findUserById(authTicket);
            return ResponseEntity.ok(UserResponse.fromUser(updatedUser));
            
        } else {
            log.severe(String.format("[UserController#updateUser] - Error updating account for user %s", id));
            
            ProblemDetail details = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            details.setTitle(ApplicationError.HTTP_ERROR);
            details.setInstance(new URI(request.getRequestURI()));
            return ResponseEntity.of(details).build();
        }
        
    }
    
    
    /**
     * Handles a request to retrieve the preferences for a given user
     * 
     * @param request       the incoming request
     * @param response      the outgoing response
     * @param id            the ID of the user for whom to retrieve the preferences
     * @return a JSON object with the user's preferences
     * @throws Exception if the request fails
     */
    @GetMapping("/{id}/preferences")
    public ResponseEntity<?> userPreferences(HttpServletRequest request,
                                             HttpServletResponse response,
                                             @PathVariable String id) 
                                                      throws Exception {
        String accessToken = (String) request.getHeader(QueryParameter.ACCESS_TOKEN);
        User user = userService.findUserById(getAuthorizationTicket(id, accessToken));
        
        if (user == null) {
            ProblemDetail details = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            details.setTitle(ApplicationError.USER_NOT_FOUND);
            details.setInstance(new URI(request.getRequestURI()));
            return ResponseEntity.of(details).build();
        } else {
            UserPreferences preferences = userService.getUserPreferences(user);
            return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(preferences.getSettings().toString());
        }
    }
    
    
    /**
     * Handles a request to update the preferences for a user
     * 
     * @param request       the incoming request
     * @param response      the outgoing response
     * @param id            the user's ID
     * @param map           an object that contains the query parameters
     * @return a JSON object with the updated user details
     * @throws Exception if the request fails
     */
    @PutMapping("/{id}/preferences")
    public ResponseEntity<?> updateUserPreferences(HttpServletRequest request,
                                                   HttpServletResponse response,
                                                   @PathVariable String id,
                                                   @RequestBody LinkedHashMap<String,Object> map) 
                                                         throws Exception {
        String accessToken = (String) request.getHeader(QueryParameter.ACCESS_TOKEN);
        LinkedHashMap settings = (LinkedHashMap)QueryUtils.getValueRequired(map, QueryParameter.SETTINGS);        
        
        AuthorizationTicket authTicket = getAuthorizationTicket(id, accessToken);
        User user = userService.findUserById(authTicket);
        
        if (user == null) {
            ProblemDetail details = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            details.setTitle(ApplicationError.USER_NOT_FOUND);
            details.setInstance(new URI(request.getRequestURI()));
            return ResponseEntity.of(details).build();
        }
        
        UserPreferences updatedPreferences = userService.updateUserPreferences(user, settings);
        return ResponseEntity.ok()
                             .contentType(MediaType.APPLICATION_JSON)
                             .body(updatedPreferences.getSettings().toString());
    }
    
}
