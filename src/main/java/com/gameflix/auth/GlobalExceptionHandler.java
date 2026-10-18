package com.gameflix.auth;

import com.gameflix.auth.dto.ErrorResponse;
import com.gameflix.auth.dto.ValidationErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions thrown by controllers and services into clean JSON error responses.
 *
 * <p>Where it sits: when anything in controller → service → repository throws one of the
 * exceptions below, Spring calls the matching method here instead of returning a 500.
 * (401s for bad tokens never reach here; Spring Security sends those before any controller runs.)
 *
 * <p>Satisfies SPEC.md section 4 ("Error handling") and section 3 (409 chosen by constraint name),
 * plus the assignment requirements for the 400, 401 and 409 status codes.
 */
// @RestControllerAdvice: the @ExceptionHandler methods in this class apply to every controller,
// and their return values are written as JSON.
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * A request body broke a validation rule (for example, an 11-character password).
     *
     * @param ex the validation failure, listing every broken rule
     * @return 400 with {@code {"message":"Invalid input","errors":{field: problem}}}
     */
    // @ExceptionHandler: this method runs when the named exception type is thrown.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // LinkedHashMap keeps the errors in the order they were found.
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // One message per field keeps the body simple; keep the first if a field broke two rules.
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ValidationErrorResponse("Invalid input", fieldErrors));
    }

    /**
     * The body could not be read: malformed JSON, the wrong JSON type, or no body at all.
     *
     * @param ex the parsing failure (its details are deliberately not returned)
     * @return 400 with {@code {"message":"Invalid input"}}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        // SECURITY: a fixed message only; the parser's own message can expose internal class names.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Invalid input"));
    }

    /**
     * The database refused an insert because a unique constraint was broken.
     *
     * @param ex the database error; its root cause names the violated constraint
     * @return 409 with "Email already registered", "Username already taken" or "Already registered"
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DataIntegrityViolationException ex) {
        // MySQL's own message (the most specific cause) looks like:
        //   Duplicate entry 'debra@example.com' for key 'users.uk_users_email'
        // We look only for the constraint name in it and never send the message itself,
        // because it contains the submitted value and internal table names.
        String databaseMessage = ex.getMostSpecificCause().getMessage();
        if (databaseMessage == null) {
            databaseMessage = "";
        }

        String clientMessage = "Already registered";
        if (databaseMessage.contains("uk_users_email")) {
            clientMessage = "Email already registered";
        } else if (databaseMessage.contains("uk_users_username")) {
            clientMessage = "Username already taken";
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(clientMessage));
    }

    /**
     * The email was unknown or the password was wrong.
     *
     * @param ex the login failure (which of the two is deliberately not recorded)
     * @return 401 with {@code {"message":"Invalid email or password"}}
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        // SECURITY: one generic message for both cases, so nobody can probe which emails exist.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Invalid email or password"));
    }
}
