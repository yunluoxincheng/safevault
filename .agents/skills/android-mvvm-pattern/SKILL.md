---
name: android-mvvm-pattern
description: Use when implementing Android applications with MVVM architecture, particularly for password managers or security-focused apps. Apply when designing ViewModel classes, LiveData usage, repository patterns, or separating business logic from UI logic in Android apps.
---

# Android MVVM Architecture Pattern

## Overview

**MVVM (Model-View-ViewModel) pattern implementation for Android apps with security considerations.** Based on SafeVault password manager implementation.

## When to Use

**Use when:**
- Building Android apps with complex UI state management
- Implementing password managers or security-focused applications
- Separating business logic from UI components
- Handling authentication and data persistence
- Managing navigation between fragments/activities
- Working with LiveData and ViewModel lifecycle

**Symptoms that indicate you need this:**
- UI code contains business logic
- Activity/Fragment classes are too large (>300 lines)
- State management is scattered across multiple components
- Data persistence logic mixed with UI rendering
- Difficulty testing UI components independently

**NOT for:**
- Simple apps with minimal state (<2 screens)
- Non-Android projects
- Projects using other architectures (MVC, MVP, MVI)

## Core Pattern

### Before (Tightly Coupled)
```java
// Activity handling everything
public class PasswordActivity extends AppCompatActivity {
    private List<PasswordItem> passwords;
    private DatabaseHelper dbHelper;
    private EditText searchView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // UI setup, database queries, business logic all mixed
    }
}
```

### After (MVVM Separation)
```java
// ViewModel handles business logic
public class PasswordViewModel extends AndroidViewModel {
    private final LiveData<List<PasswordItem>> passwords;
    private final PasswordRepository repository;
    
    public LiveData<List<PasswordItem>> searchPasswords(String query) {
        // Business logic here
    }
}

// Fragment handles UI only
public class PasswordListFragment extends Fragment {
    private PasswordViewModel viewModel;
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel.getPasswords().observe(getViewLifecycleOwner(), passwords -> {
            // Update UI only
            adapter.submitList(passwords);
        });
    }
}
```

## Quick Reference

| Component | Responsibility | SafeVault Example |
|-----------|----------------|-------------------|
| **Model** | Data and business logic | `PasswordItem`, `BackendService` |
| **View** | UI components | `PasswordListFragment`, `LoginActivity` |
| **ViewModel** | UI state and presentation logic | `PasswordViewModel`, `AuthViewModel` |
| **Repository** | Data abstraction layer | `PasswordRepository` (implied) |
| **LiveData** | Observable data holder | `MutableLiveData<String>` |

## Implementation Guidelines

### 1. ViewModel Structure
```java
public class AuthViewModel extends AndroidViewModel {
    // Use MutableLiveData for state that UI observes
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    
    // Use CompositeDisposable for RxJava cleanup
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    // Inject dependencies through constructor
    private final BackendService backendService;
    private final TokenManager tokenManager;
    
    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.backendService = ServiceLocator.getInstance().getBackendService();
        this.tokenManager = TokenManager.getInstance(application);
    }
    
    // Expose immutable LiveData to UI
    public LiveData<AuthState> getAuthState() {
        return authState;
    }
    
    // Business logic methods
    public void login(String username, String password) {
        isLoading.setValue(true);
        
        Disposable disposable = backendService.authenticate(username, password)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    isLoading.setValue(false);
                    tokenManager.saveTokens(response);
                    authState.setValue(AuthState.AUTHENTICATED);
                },
                error -> {
                    isLoading.setValue(false);
                    errorMessage.setValue("Login failed: " + error.getMessage());
                }
            );
            
        disposables.add(disposable);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear(); // Clean up RxJava subscriptions
    }
}
```

### 2. Fragment/Activity Usage
```java
public class LoginActivity extends AppCompatActivity {
    private AuthViewModel viewModel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        // Observe LiveData
        viewModel.getAuthState().observe(this, authState -> {
            if (authState == AuthState.AUTHENTICATED) {
                navigateToMain();
            }
        });
        
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                showError(error);
            }
        });
        
        viewModel.getLoading().observe(this, loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        });
    }
    
    private void onLoginButtonClick() {
        String username = editUsername.getText().toString();
        String password = editPassword.getText().toString();
        viewModel.login(username, password); // Delegate to ViewModel
    }
}
```

### 3. Security Considerations
- **Never store sensitive data in ViewModel**: ViewModels survive configuration changes
- **Use AndroidKeyStore for cryptographic keys**: `KeyManager.getInstance(context)`
- **Clear sensitive data in onCleared()**: Especially RxJava disposables
- **Apply FLAG_SECURE in UI layer**: Not in ViewModel

### 4. Testing Strategy
```java
@RunWith(AndroidJUnit4.class)
public class AuthViewModelTest {
    private AuthViewModel viewModel;
    private TestApplication application;
    
    @Before
    public void setup() {
        application = new TestApplication();
        viewModel = new AuthViewModel(application);
    }
    
    @Test
    public void testLoginSuccess() {
        // Arrange
        TestObserver<AuthState> observer = viewModel.getAuthState().test();
        
        // Act
        viewModel.login("test", "password");
        
        // Assert
        observer.assertValue(AuthState.AUTHENTICATED);
    }
}
```

## Common Mistakes

| Mistake | Problem | Solution |
|---------|---------|----------|
| **Business logic in Fragment** | Hard to test, violates separation | Move to ViewModel |
| **Direct database access in ViewModel** | Tight coupling, hard to mock | Use Repository pattern |
| **Not handling configuration changes** | Data loss on rotation | Use ViewModel + LiveData |
| **Memory leaks with RxJava** | Disposables not cleaned | Use CompositeDisposable |
| **Sensitive data in ViewModel** | Security risk | Store in secure storage |
| **UI logic in ViewModel** | Hard to test UI-specific behavior | Keep in Fragment/Activity |

## SafeVault Specific Patterns

### 1. ServiceLocator Pattern
```java
// Centralized service access
public class ServiceLocator {
    private static ServiceLocator instance;
    private BackendService backendService;
    
    public static ServiceLocator getInstance() {
        if (instance == null) {
            instance = new ServiceLocator();
        }
        return instance;
    }
    
    public BackendService getBackendService() {
        if (backendService == null) {
            backendService = new BackendServiceImpl();
        }
        return backendService;
    }
}

// ViewModel usage
public class MyViewModel extends AndroidViewModel {
    private final BackendService backendService;
    
    public MyViewModel(@NonNull Application application) {
        super(application);
        this.backendService = ServiceLocator.getInstance().getBackendService();
    }
}
```

### 2. Token Management Integration
```java
public class AuthViewModel extends AndroidViewModel {
    private final TokenManager tokenManager;
    
    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.tokenManager = TokenManager.getInstance(application);
    }
    
    public boolean isLoggedIn() {
        return tokenManager.isLoggedIn();
    }
    
    public void logout() {
        tokenManager.clearTokens();
        // Update UI state
        authState.setValue(AuthState.LOGGED_OUT);
    }
}
```

### 3. Navigation with Safe Args
```java
// In ViewModel - prepare navigation data
public void navigateToPasswordDetail(int passwordId) {
    navigationCommand.setValue(
        new NavigationCommand(R.id.action_to_password_detail, passwordId)
    );
}

// In Fragment - handle navigation
viewModel.getNavigationCommand().observe(getViewLifecycleOwner(), command -> {
    if (command != null) {
        NavDirections directions = 
            PasswordListFragmentDirections.actionToPasswordDetail(command.getPasswordId());
        Navigation.findNavController(requireView()).navigate(directions);
    }
});
```

## Red Flags

**STOP and refactor if:**
- Fragment/Activity > 300 lines
- Business logic in `onCreate()` or `onViewCreated()`
- Direct `SharedPreferences` or database access in UI layer
- No separation between data source and UI logic
- ViewModel contains Android framework references (Context, Resources)
- RxJava subscriptions not properly disposed

**CRITICAL**: Always clear sensitive data in `onCleared()` method of ViewModel.

## References

- Android Developer Guide: ViewModel
- SafeVault Implementation: `AuthViewModel.java`, `PasswordViewModel.java`
- Android Architecture Components: LiveData, ViewModel
- RxJava Android: Schedulers and Disposables