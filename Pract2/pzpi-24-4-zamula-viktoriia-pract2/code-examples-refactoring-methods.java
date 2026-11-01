package com.example;

public class RefactoringExamples {

    // В.1 та В.2 Об'єднання умов (Consolidate Conditional Expression)
    public static class LoanCalculatorBefore {
        int debtCount;
        boolean isBlocked;
        int baseLimit = 1000;

        int calculateLoanLimit() {
            if (debtCount > 0) {
                return 0;
            }
            if (isBlocked) {
                return 0;
            }
            return baseLimit;
        }
    }

    public static class LoanCalculatorAfter {
        int debtCount;
        boolean isBlocked;
        int baseLimit = 1000;

        int calculateLoanLimit() {
            if (isLoanForbidden()) {
                return 0;
            }
            return baseLimit;
        }

        boolean isLoanForbidden() {
            return debtCount > 0 || isBlocked;
        }
    }

    // В.3 Клас User із публічним полем до інкапсуляції
    public static class UserBefore {
        public String firstName;
        public String lastName;

        // В.5 Метод getlnm до перейменування
        public String getlnm() {
            return lastName;
        }
    }

    public static class UserBeforeUsage {
        public void example() {
            UserBefore user = new UserBefore();
            // Usage in other classes:
            user.firstName = "Olena";
            String first = user.firstName;
        }
    }

    // В.4 Поле name класу User після інкапсуляції
    // В.6 Метод getLastName після перейменування
    public static class UserAfter {
        private String firstName;
        private String lastName;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            if (firstName != null && !firstName.isEmpty()) {
                this.firstName = firstName;
            }
        }

        // В.6 Метод getLastName після перейменування
        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }
}