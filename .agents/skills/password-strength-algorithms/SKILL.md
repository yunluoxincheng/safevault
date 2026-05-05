---
name: password-strength-algorithms
description: Use when implementing password strength calculation, password generation, or security assessment features in Android apps. Apply for password managers, security tools, or any application requiring password quality evaluation and secure password generation.
---

# Password Strength Calculation and Generation Algorithms

## Overview

**Comprehensive password strength assessment and secure password generation algorithms.** Based on SafeVault's implementation including strength scoring, visual indicators, and configurable password generation with security best practices.

## When to Use

**Use when:**
- Building password managers or vault applications
- Implementing password strength indicators
- Creating secure password generators
- Assessing password quality for user registration
- Providing password security feedback
- Generating memorable yet secure passwords
- Implementing password policy enforcement

**Symptoms that indicate you need this:**
- No password strength feedback for users
- Weak password generation algorithms
- Inconsistent strength scoring
- No visual indicators for password quality
- Passwords not meeting security requirements
- Need for configurable password generation

**NOT for:**
- Simple password fields without validation
- Non-security-focused applications
- Password storage without generation

## Core Pattern

### Before (Simple Validation)
```java
// Basic length check only
public boolean isValidPassword(String password) {
    return password != null && password.length() >= 8;
}
```

### After (Comprehensive Strength Assessment)
```java
// Full strength calculation with visual feedback
public PasswordStrength calculateStrength(String password) {
    int score = calculateScore(password);
    PasswordStrength.Level level = determineLevel(score);
    String feedback = generateFeedback(password, score);
    
    return new PasswordStrength(score, level, feedback);
}
```

## Quick Reference

| Component | Purpose | SafeVault Example |
|-----------|---------|-------------------|
| **Strength Calculator** | Score passwords 0-100 | `PasswordStrengthCalculator.java` |
| **Password Generator** | Generate secure passwords | `PasswordGenerator.java` |
| **Strength Model** | Data structure for results | `PasswordStrength.java` |
| **Visual Indicator** | UI progress bar/color | `GeneratorFragment.java` |
| **Feedback Generator** | User guidance | `PasswordStrengthCalculator.java` |

## Implementation Guidelines

### 1. Password Strength Model
```java
public class PasswordStrength {
    public enum Level {
        WEAK(0xFFF44336),    // Red
        MEDIUM(0xFFFF9800),   // Orange
        STRONG(0xFF4CAF50),   // Green
        VERY_STRONG(0xFF2196F3); // Blue
        
        private final int color;
        
        Level(int color) {
            this.color = color;
        }
        
        public int getColor() {
            return color;
        }
    }
    
    private final int score; // 0-100
    private final Level level;
    private final String feedback;
    
    public PasswordStrength(int score, Level level, String feedback) {
        this.score = score;
        this.level = level;
        this.feedback = feedback;
    }
    
    // Factory method from score
    public static PasswordStrength fromScore(int score) {
        Level level;
        String feedback;
        
        if (score < 30) {
            level = Level.WEAK;
            feedback = "Too weak - add more characters and types";
        } else if (score < 60) {
            level = Level.MEDIUM;
            feedback = "Moderate - could be stronger";
        } else if (score < 85) {
            level = Level.STRONG;
            feedback = "Strong password";
        } else {
            level = Level.VERY_STRONG;
            feedback = "Very strong password!";
        }
        
        return new PasswordStrength(score, level, feedback);
    }
    
    // Getters
    public int getScore() { return score; }
    public Level getLevel() { return level; }
    public String getFeedback() { return feedback; }
    public boolean isStrong() { return level == Level.STRONG || level == Level.VERY_STRONG; }
}
```

### 2. Strength Calculator
```java
public class PasswordStrengthCalculator {
    
    /**
     * Calculate password strength score (0-100)
     */
    public static int calculateScore(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        
        double score = 0;
        int length = password.length();
        
        // Length contribution (max 40 points)
        score += Math.min(length * 2.5, 40);
        
        // Character type bonuses (max 30 points)
        boolean hasLower = containsLowercase(password);
        boolean hasUpper = containsUppercase(password);
        boolean hasDigit = containsDigit(password);
        boolean hasSymbol = containsSymbol(password);
        
        int typeCount = 0;
        if (hasLower) { typeCount++; score += 7.5; }
        if (hasUpper) { typeCount++; score += 7.5; }
        if (hasDigit) { typeCount++; score += 7.5; }
        if (hasSymbol) { typeCount++; score += 7.5; }
        
        // Complexity bonus (max 15 points)
        if (typeCount >= 3) score += 10;
        if (typeCount == 4) score += 5;
        
        // Entropy calculation (max 15 points)
        double entropy = calculateEntropy(password);
        score += Math.min(entropy / 2, 15);
        
        // Penalties for patterns
        score -= calculatePenalties(password);
        
        return (int) Math.min(Math.max(score, 0), 100);
    }
    
    private static boolean containsLowercase(String password) {
        return password.matches(".*[a-z].*");
    }
    
    private static boolean containsUppercase(String password) {
        return password.matches(".*[A-Z].*");
    }
    
    private static boolean containsDigit(String password) {
        return password.matches(".*\\d.*");
    }
    
    private static boolean containsSymbol(String password) {
        return password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*");
    }
    
    private static double calculateEntropy(String password) {
        // Calculate character set size
        int charsetSize = 0;
        if (containsLowercase(password)) charsetSize += 26;
        if (containsUppercase(password)) charsetSize += 26;
        if (containsDigit(password)) charsetSize += 10;
        if (containsSymbol(password)) charsetSize += 32; // Common symbols
        
        if (charsetSize == 0) return 0;
        
        // Entropy = log2(charsetSize^length)
        return password.length() * (Math.log(charsetSize) / Math.log(2));
    }
    
    private static double calculatePenalties(String password) {
        double penalty = 0;
        
        // Repeated characters
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                penalty += 5;
            }
        }
        
        // Sequential characters (abc, 123, etc.)
        for (int i = 2; i < password.length(); i++) {
            char c1 = password.charAt(i - 2);
            char c2 = password.charAt(i - 1);
            char c3 = password.charAt(i);
            
            if (isSequential(c1, c2, c3)) {
                penalty += 10;
            }
        }
        
        // Common patterns
        if (password.matches(".*(password|123456|qwerty).*")) {
            penalty += 20;
        }
        
        return penalty;
    }
    
    private static boolean isSequential(char c1, char c2, char c3) {
        return (c2 == c1 + 1 && c3 == c2 + 1) || // Ascending
               (c2 == c1 - 1 && c3 == c2 - 1);   // Descending
    }
    
    /**
     * Get strength level from score
     */
    public static PasswordStrength.Level getStrengthLevel(int score) {
        if (score < 30) return PasswordStrength.Level.WEAK;
        if (score < 60) return PasswordStrength.Level.MEDIUM;
        if (score < 85) return PasswordStrength.Level.STRONG;
        return PasswordStrength.Level.VERY_STRONG;
    }
    
    /**
     * Get detailed feedback for password
     */
    public static String getFeedback(String password) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty";
        }
        
        StringBuilder feedback = new StringBuilder();
        
        // Length feedback
        if (password.length() < 8) {
            feedback.append("Too short (minimum 8 characters). ");
        } else if (password.length() < 12) {
            feedback.append("Consider using 12+ characters. ");
        }
        
        // Character type feedback
        if (!containsLowercase(password)) {
            feedback.append("Add lowercase letters. ");
        }
        if (!containsUppercase(password)) {
            feedback.append("Add uppercase letters. ");
        }
        if (!containsDigit(password)) {
            feedback.append("Add numbers. ");
        }
        if (!containsSymbol(password)) {
            feedback.append("Add symbols. ");
        }
        
        // Pattern warnings
        if (password.matches(".*(.)\\1{2,}.*")) {
            feedback.append("Avoid repeated characters. ");
        }
        
        if (feedback.length() == 0) {
            feedback.append("Strong password!");
        }
        
        return feedback.toString().trim();
    }
}
```

### 3. Password Generator
```java
public class PasswordGenerator {
    
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * Generate password with specified criteria
     */
    public static String generatePassword(int length, boolean includeLowercase,
                                         boolean includeUppercase, boolean includeDigits,
                                         boolean includeSymbols) {
        if (length < 4) {
            throw new IllegalArgumentException("Password length must be at least 4");
        }
        
        // Build character set based on options
        StringBuilder charset = new StringBuilder();
        if (includeLowercase) charset.append(LOWERCASE);
        if (includeUppercase) charset.append(UPPERCASE);
        if (includeDigits) charset.append(DIGITS);
        if (includeSymbols) charset.append(SYMBOLS);
        
        if (charset.length() == 0) {
            throw new IllegalArgumentException("At least one character type must be selected");
        }
        
        // Generate password ensuring at least one of each selected type
        char[] password = new char[length];
        boolean[] typeIncluded = new boolean[4]; // lower, upper, digit, symbol
        
        for (int i = 0; i < length; i++) {
            password[i] = charset.charAt(random.nextInt(charset.length()));
            
            // Track which types are included
            char c = password[i];
            if (LOWERCASE.indexOf(c) >= 0) typeIncluded[0] = true;
            else if (UPPERCASE.indexOf(c) >= 0) typeIncluded[1] = true;
            else if (DIGITS.indexOf(c) >= 0) typeIncluded[2] = true;
            else if (SYMBOLS.indexOf(c) >= 0) typeIncluded[3] = true;
        }
        
        // Ensure at least one of each selected type is included
        ensureTypeIncluded(password, includeLowercase, typeIncluded[0], LOWERCASE);
        ensureTypeIncluded(password, includeUppercase, typeIncluded[1], UPPERCASE);
        ensureTypeIncluded(password, includeDigits, typeIncluded[2], DIGITS);
        ensureTypeIncluded(password, includeSymbols, typeIncluded[3], SYMBOLS);
        
        // Shuffle to avoid predictable positions
        shuffleArray(password);
        
        return new String(password);
    }
    
    private static void ensureTypeIncluded(char[] password, boolean shouldInclude,
                                          boolean isIncluded, String charset) {
        if (shouldInclude && !isIncluded) {
            // Replace a random character with one from the missing charset
            int index = random.nextInt(password.length);
            password[index] = charset.charAt(random.nextInt(charset.length()));
        }
    }
    
    private static void shuffleArray(char[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            char temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }
    
    /**
     * Generate memorable password (pronounceable)
     */
    public static String generateMemorablePassword(int wordCount) {
        // Common syllable patterns for pronounceable passwords
        String[] syllables = {
            "ba", "be", "bi", "bo", "bu",
            "ca", "ce", "ci", "co", "cu",
            "da", "de", "di", "do", "du",
            "fa", "fe", "fi", "fo", "fu",
            "ga", "ge", "gi", "go", "gu",
            "ha", "he", "hi", "ho", "hu",
            "ja", "je", "ji", "jo", "ju",
            "ka", "ke", "ki", "ko", "ku",
            "la", "le", "li", "lo", "lu",
            "ma", "me", "mi", "mo", "mu",
            "na", "ne", "ni", "no", "nu",
            "pa", "pe", "pi", "po", "pu",
            "ra", "re", "ri", "ro", "ru",
            "sa", "se", "si", "so", "su",
            "ta", "te", "ti", "to", "tu",
            "va", "ve", "vi", "vo", "vu",
            "wa", "we", "wi", "wo", "wu",
            "xa", "xe", "xi", "xo", "xu",
            "ya", "ye", "yi", "yo", "yu",
            "za", "ze", "zi", "zo", "zu"
        };
        
        // Common number substitutions
        Map<Character, Character> numberSubs = new HashMap<>();
        numberSubs.put('a', '4');
        numberSubs.put('e', '3');
        numberSubs.put('i', '1');
        numberSubs.put('o', '0');
        numberSubs.put('s', '5');
        numberSubs.put('t', '7');
        
        StringBuilder password = new StringBuilder();
        
        for (int i = 0; i < wordCount; i++) {
            // Build word from 2-3 syllables
            int syllableCount = 2 + random.nextInt(2); // 2 or 3 syllables
            StringBuilder word = new StringBuilder();
            
            for (int j = 0; j < syllableCount; j++) {
                word.append(syllables[random.nextInt(syllables.length)]);
            }
            
            // Capitalize first letter
            word.setCharAt(0, Character.toUpperCase(word.charAt(0)));
            
            // Possibly add number substitution
            if (random.nextBoolean()) {
                char[] wordChars = word.toString().toCharArray();
                for (int k = 0; k < wordChars.length; k++) {
                    if (numberSubs.containsKey(Character.toLowerCase(wordChars[k])) && 
                        random.nextDouble() < 0.3) {
                        wordChars[k] = numberSubs.get(Character.toLowerCase(wordChars[k]));
                    }
                }
                word = new StringBuilder(new String(wordChars));
            }
            
            password.append(word);
            
            // Add separator between words (except last)
            if (i < wordCount - 1) {
                char[] separators = {'-', '_', '.', '!'};
                password.append(separators[random.nextInt(separators.length)]);
            }
        }
        
        return password.toString();
    }
    
    /**
     * Generate PIN code
     */
    public static String generatePIN(int length) {
        if (length < 4 || length > 8) {
            throw new IllegalArgumentException("PIN length must be between 4 and 8");
        }
        
        StringBuilder pin = new StringBuilder();
        for (int i = 0; i < length; i++) {
            pin.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        
        // Ensure no sequential or repeated patterns
        String pinStr = pin.toString();
        if (hasSequentialDigits(pinStr) || hasRepeatedDigits(pinStr)) {
            // Regenerate if pattern detected
            return generatePIN(length);
        }
        
        return pinStr;
    }
    
    private static boolean hasSequentialDigits(String pin) {
        for (int i = 2; i < pin.length(); i++) {
            int d1 = pin.charAt(i - 2) - '0';
            int d2 = pin.charAt(i - 1) - '0';
            int d3 = pin.charAt(i) - '0';
            
            if ((d2 == d1 + 1 && d3 == d2 + 1) || // Ascending
                (d2 == d1 - 1 && d3 == d2 - 1)) { // Descending
                return true;
            }
        }
        return false;
    }
    
    private static boolean hasRepeatedDigits(String pin) {
        for (int i = 2; i < pin.length(); i++) {
            if (pin.charAt(i) == pin.charAt(i - 1) && 
                pin.charAt(i) == pin.charAt(i - 2)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Generate password with preset configurations
     */
    public static GeneratedPassword generateWithPreset(Preset preset) {
        switch (preset) {
            case PIN:
                String pin = generatePIN(6);
                return new GeneratedPassword(pin, "6-digit PIN");
                
            case MEMORABLE:
                String memorable = generateMemorablePassword(3);
                return new GeneratedPassword(memorable, "Memorable password");
                
            case STRONG:
                String strong = generatePassword(16, true, true, true, true);
                return new GeneratedPassword(strong, "Strong password");
                
            case VERY_STRONG:
                String veryStrong = generatePassword(20, true, true, true, true);
                return new GeneratedPassword(veryStrong, "Very strong password");
                
            default:
                throw new IllegalArgumentException("Unknown preset: " + preset);
        }
    }
    
    public enum Preset {
        PIN, MEMORABLE, STRONG, VERY_STRONG
    }
    
    public static class GeneratedPassword {
        private final String password;
        private final String description;
        private final Date generatedAt;
        
        public GeneratedPassword(String password, String description) {
            this.password = password;
            this.description = description;
            this.generatedAt = new Date();
        }
        
        // Getters
        public String getPassword() { return password; }
        public String getDescription() { return description; }
        public Date getGeneratedAt() { return generatedAt; }
        
        public PasswordStrength getStrength() {
            return PasswordStrength.fromScore(
                PasswordStrengthCalculator.calculateScore(password)
            );
        }
    }
}
```

### 4. UI Integration (ViewModel)
```java
public class GeneratorViewModel extends AndroidViewModel {
    
    private final MutableLiveData<String> generatedPassword = new MutableLiveData<>();
    private final MutableLiveData<PasswordStrength> passwordStrength = new MutableLiveData<>();
    private final MutableLiveData<List<GeneratedPassword>> passwordHistory = new MutableLiveData<>();
    
    // Configuration
    private int passwordLength = 16;
    private boolean includeLowercase = true;
    private boolean includeUppercase = true;
    private boolean includeDigits = true;
    private boolean includeSymbols = true;
    
    // History storage (max 10 items)
    private final List<GeneratedPassword> history = new ArrayList<>();
    private static final int MAX_HISTORY = 10;
    
    public GeneratorViewModel(@NonNull Application application) {
        super(application);
    }
    
    /**
     * Generate new password with current configuration
     */
    public void generatePassword() {
        String password = PasswordGenerator.generatePassword(
            passwordLength,
            includeLowercase,
            includeUppercase,
            includeDigits,
            includeSymbols
        );
        
        generatedPassword.setValue(password);
        
        // Calculate strength
        PasswordStrength strength = PasswordStrengthCalculator.calculate(password);
        passwordStrength.setValue(strength);
        
        // Add to history
        GeneratedPassword genPass = new GeneratedPassword(
            password,
            getConfigurationDescription()
        );
        
        history.add(0, genPass); // Add to beginning
        if (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1); // Remove oldest
        }
        
        passwordHistory.setValue(new ArrayList<>(history));
    }
    
    /**
     * Generate password with preset
     */
    public void generateWithPreset(PasswordGenerator.Preset preset) {
        PasswordGenerator.GeneratedPassword genPass = 
            PasswordGenerator.generateWithPreset(preset);
        
        generatedPassword.setValue(genPass.getPassword());
        passwordStrength.setValue(genPass.getStrength());
        
        // Update configuration based on preset
        updateConfigurationForPreset(preset);
        
        // Add to history
        history.add(0, genPass);
        if (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        
        passwordHistory.setValue(new ArrayList<>(history));
    }
    
    private void updateConfigurationForPreset(PasswordGenerator.Preset preset) {
        switch (preset) {
            case PIN:
                passwordLength = 6;
                includeLowercase = false;
                includeUppercase = false;
                includeDigits = true;
                includeSymbols = false;
                break;
                
            case MEMORABLE:
                passwordLength = 12;
                includeLowercase = true;
                includeUppercase = true;
                includeDigits = true;
                includeSymbols = false;
                break;
                
            case STRONG:
                passwordLength = 16;
                includeLowercase = true;
                includeUppercase = true;
                includeDigits = true;
                includeSymbols = true;
                break;
                
            case VERY_STRONG:
                passwordLength = 20;
                includeLowercase = true;
                includeUppercase = true;
                includeDigits = true;
                includeSymbols = true;
                break;
        }
    }
    
    private String getConfigurationDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(passwordLength).append(" chars: ");
        
        List<String> types = new ArrayList<>();
        if (includeLowercase) types.add("lowercase");
        if (includeUppercase) types.add("uppercase");
        if (includeDigits) types.add("digits");
        if (includeSymbols) types.add("symbols");
        
        desc.append(String.join(", ", types));
        return desc.toString();
    }
    
    /**
     * Clear password history
     */
    public void clearHistory() {
        history.clear();
        passwordHistory.setValue(new ArrayList<>(history));
    }
    
    /**
     * Validate current configuration
     */
    public boolean isConfigurationValid() {
        return passwordLength >= 4 && 
               (includeLowercase || includeUppercase || includeDigits || includeSymbols);
    }
    
    // Configuration setters
    public void setPasswordLength(int length) {
        this.passwordLength = Math.max(4, Math.min(length, 32));
    }
    
    public void setIncludeLowercase(boolean include) {
        this.includeLowercase = include;
    }
    
    public void setIncludeUppercase(boolean include) {
        this.includeUppercase = include;
    }
    
    public void setIncludeDigits(boolean include) {
        this.includeDigits = include;
    }
    
    public void setIncludeSymbols(boolean include) {
        this.includeSymbols = include;
    }
    
    // Getters for LiveData
    public LiveData<String> getGeneratedPassword() {
        return generatedPassword;
    }
    
    public LiveData<PasswordStrength> getPasswordStrength() {
        return passwordStrength;
    }
    
    public LiveData<List<GeneratedPassword>> getPasswordHistory() {
        return passwordHistory;
    }
    
    // Getters for configuration
    public int getPasswordLength() { return passwordLength; }
    public boolean isIncludeLowercase() { return includeLowercase; }
    public boolean isIncludeUppercase() { return includeUppercase; }
    public boolean isIncludeDigits() { return includeDigits; }
    public boolean isIncludeSymbols() { return includeSymbols; }
}
```

### 5. UI Component Integration
```java
// In Fragment or Activity
public void setupPasswordStrengthIndicator(EditText passwordField, ProgressBar strengthBar) {
    passwordField.addTextChangedListener(new TextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            String password = s.toString();
            
            // Calculate strength
            PasswordStrength strength = PasswordStrengthCalculator.calculate(password);
            
            // Update progress bar
            strengthBar.setProgress(strength.getScore());
            
            // Update color based on level
            int color = strength.getLevel().getColor();
            strengthBar.setProgressTintList(ColorStateList.valueOf(color));
            
            // Show feedback
            feedbackTextView.setText(strength.getFeedback());
        }
        
        // Other TextWatcher methods...
    });
}
```

## Security Considerations

### 1. Secure Random Number Generation
```java
// Always use SecureRandom for cryptographic operations
private static final SecureRandom secureRandom = new SecureRandom();

public static String generateCryptoSecurePassword(int length) {
    // Use cryptographic-grade random
    byte[] randomBytes = new byte[length];
    secureRandom.nextBytes(randomBytes);
    
    // Convert to base64 for readable password
    return Base64.getEncoder().encodeToString(randomBytes)
        .replaceAll("[+/=]", "").substring(0, length);
}
```

### 2. Password Policy Enforcement
```java
public class PasswordPolicy {
    
    public static ValidationResult validate(String password) {
        List<String> errors = new ArrayList<>();
        
        // Length requirement
        if (password.length() < 8) {
            errors.add("Password must be at least 8 characters");
        }
        
        // Character type requirements
        if (!password.matches(".*[a-z].*")) {
            errors.add("Password must contain lowercase letters");
        }
        if (!password.matches(".*[A-Z].*")) {
            errors.add("Password must contain uppercase letters");
        }
        if (!password.matches(".*\\d.*")) {
            errors.add("Password must contain numbers");
        }
        if (!password.matches(".*[!@#$%^&*()].*")) {
            errors.add("Password must contain special characters");
        }
        
        // Strength requirement
        int score = PasswordStrengthCalculator.calculateScore(password);
        if (score < 60) {
            errors.add("Password is too weak (score: " + score + "/100)");
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        
        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public String getErrorMessage() {
            return String.join("\n", errors);
        }
    }
}
```

## Testing

```java
@RunWith(AndroidJUnit4.class)
public class PasswordStrengthTest {
    
    @Test
    public void testStrengthCalculation() {
        // Weak password
        PasswordStrength weak = PasswordStrengthCalculator.calculate("123");
        assertEquals(PasswordStrength.Level.WEAK, weak.getLevel());
        assertTrue(weak.getScore() < 30);
        
        // Strong password
        PasswordStrength strong = PasswordStrengthCalculator.calculate("StrongP@ssw0rd123!");
        assertEquals(PasswordStrength.Level.STRONG, strong.getLevel());
        assertTrue(strong.getScore() >= 60);
    }
    
    @Test
    public void testPasswordGeneration() {
        // Test different configurations
        String pin = PasswordGenerator.generatePIN(6);
        assertEquals(6, pin.length());
        assertTrue(pin.matches("\\d{6}"));
        
        String strong = PasswordGenerator.generatePassword(16, true, true, true, true);
        assertEquals(16, strong.length());
        assertTrue(strong.matches(".*[a-z].*")); // Has lowercase
        assertTrue(strong.matches(".*[A-Z].*")); // Has uppercase
        assertTrue(strong.matches(".*\\d.*"));   // Has digit
        assertTrue(strong.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?].*")); // Has symbol
    }
    
    @Test
    public void testEntropyCalculation() {
        // Simple password has low entropy
        double entropy1 = PasswordStrengthCalculator.calculateEntropy("123456");
        assertTrue(entropy1 < 20);
        
        // Complex password has high entropy
        double entropy2 = PasswordStrengthCalculator.calculateEntropy("A1b@C3d$E5f^");
        assertTrue(entropy2 > 50);
    }
}
```

## Common Mistakes

| Mistake | Problem | Solution |
|---------|---------|----------|
| **Using Math.random()** | Not cryptographically secure | Use SecureRandom |
| **No entropy calculation** | Weak passwords considered strong | Calculate character set entropy |
| **Ignoring patterns** | "123456" passes length check | Detect sequences and repeats |
| **No user feedback** | Users don't know how to improve | Provide specific guidance |
| **Weak default settings** | Generates insecure passwords | Default to strong settings |
| **No history management** | Can't reuse good passwords | Store recent generations |
| **Inconsistent scoring** | Same password scores differently | Use deterministic algorithm |

## SafeVault Specific Implementation

### 1. Real-time Strength Updates
```java
// In GeneratorFragment
private void setupRealTimeStrength() {
    binding.passwordLengthSlider.addOnChangeListener((slider, value, fromUser) -> {
        viewModel.setPasswordLength((int) value);
        binding.lengthValue.setText(String.valueOf((int) value));
        
        // Regenerate with new length
        if (autoGenerate.isChecked()) {
            viewModel.generatePassword();
        }
    });
    
    // Listen for character type toggles
    binding.includeLowercase.setOnCheckedChangeListener((button, checked) -> {
        viewModel.setIncludeLowercase(checked);
        if (autoGenerate.isChecked()) {
            viewModel.generatePassword();
        }
    });
    
    // Update strength display when password changes
    viewModel.getPasswordStrength().observe(getViewLifecycleOwner(), strength -> {
        binding.strengthBar.setProgress(strength.getScore());
        binding.strengthText.setText(strength.getFeedback());
        
        // Update color
        int color = ContextCompat.getColor(requireContext(), 
            getColorForLevel(strength.getLevel()));
        binding.strengthBar.setProgressTintList(ColorStateList.valueOf(color));
    });
}
```

### 2. Password History with Security
```java
public class PasswordHistoryManager {
    
    /**
     * Store generated password in history (encrypted)
     */
    public void addToHistory(GeneratedPassword password) {
        // Encrypt before storage
        String encrypted = encryptPassword(password.getPassword());
        
        // Store with metadata
        HistoryEntry entry = new HistoryEntry(
            encrypted,
            password.getDescription(),
            password.getGeneratedAt(),
            password.getStrength()
        );
        
        // Save to secure storage
        saveEntry(entry);
    }
    
    /**
     * Retrieve password from history (decrypted on demand)
     */
    public GeneratedPassword getFromHistory(int index) {
        HistoryEntry entry = loadEntry(index);
        
        // Decrypt when needed
        String decrypted = decryptPassword(entry.getEncryptedPassword());
        
        return new GeneratedPassword(
            decrypted,
            entry.getDescription(),
            entry.getGeneratedAt()
        );
    }
    
    /**
     * Clear history (securely wipe)
     */
    public void clearHistory() {
        // Securely delete all entries
        secureWipeHistory();
    }
}
```

## Red Flags

**STOP and fix if:**
- Using `Math.random()` for password generation
- No strength feedback for users
- Weak default password settings
- Passwords stored in history without encryption
- No entropy calculation in strength assessment
- Ignoring common weak patterns (123456, password, etc.)
- No minimum strength enforcement

**CRITICAL**: Always use `SecureRandom` for cryptographic operations.

## References

- NIST Password Guidelines: https://pages.nist.gov/800-63-3/sp800-63b.html
- OWASP Password Storage Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- Password Strength Meters: https://www.usenix.org/conference/soups2018/presentation/mazurek
- SafeVault Implementation: `PasswordStrengthCalculator.java`, `PasswordGenerator.java`, `GeneratorViewModel.java`
- Android SecureRandom: https://developer.android.com/reference/java/security/SecureRandom