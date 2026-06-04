Scenario: Requested loans with small down payments must be denied

Given the user navigates to the url 'https://para.testar.org/'
When the user logs in with the john/demo credentials
And the user navigates to request a loan
And the user fills out a big loan amount with a small down payment
And the user selects the account 13011 and applies for the loan
Then a message indicates the loan is denied
