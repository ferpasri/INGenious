Scenario: User can log in to Parabank

Given the user navigates to the url 'https://para.testar.org/'
When the user logs in with the john/demo credentials
Then a welcome john smith message is shown
