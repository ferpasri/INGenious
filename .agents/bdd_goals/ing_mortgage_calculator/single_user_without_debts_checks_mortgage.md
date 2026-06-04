Scenario: Single user without debts checks mortgage options

Given the user navigates to the url 'https://www.ing.nl/en/personal/mortgage/calculate-mortgage/based-on-income'
And the user agrees to save basic cookies if present
When the user is a single adult in their thirties
And the user wants to buy a home within the next 5 months
And the user prefers a high energy label
And the user is employed with a permanent contract
And the user income is more than 50k
And the user does not have any debts
Then the mortgage calculator shows the maximum amount the user can borrow
