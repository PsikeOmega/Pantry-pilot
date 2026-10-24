# Pantry Pilot

An offline Android household checklist and shopping app. The project uses Kotlin,
Jetpack Compose, and package `com.psike.pantrypilot`.

## Current feature: quantities and weekly refill

- Add and edit items with a positive quantity (whole numbers or decimals) and an
  optional unit such as cans, bags, or lb.
- Mark an item **Have** or **Need**. Need sends this week's quantity to Shopping;
  purchases can be checked off there.
- Enable **Refill checklist each week** and set a **Saved weekly quantity**.
  This is stored separately from **Quantity this week**. For example, a recurring
  item programmed as 4 cans can be changed to 2 cans for this trip, then return as
  4 cans the next week. Only editing the saved weekly field changes that default.
- Use **More → Start new week** and confirm. Recurring items return with their
  saved quantities and unanswered Have/Need choices. One-time checklist entries
  are removed. Shopping entries, quantities, and purchased states remain intact.
- Lists and quantities persist locally across app restarts. Weekly reset is a
  deliberate action, with no calendar-triggered background reset.

Marking Need again replaces that item's shopping entry rather than creating a
duplicate or adding the quantities together. Changing an unpurchased item on the
checklist updates its shopping quantity. Marking Have removes an unpurchased
shopping entry. Removing a checklist item leaves its shopping entry intact.

## Remaining planned work

Store/department/aisle metadata and sorting, first-purchase location prompts,
price tracking and optional price-update prompts, and selectable light/dark/system
themes. The current appearance follows the phone's theme.

Sharing and syncing are deferred. Recipes, detailed pantry inventory, and
printing/PDF are outside the first release.

## Build and validation

With JDK 17 and Android SDK 35 installed:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions runs those checks and uploads a `Pantry-Pilot-debug` APK artifact,
test reports, and the build log. Download and extract the artifact to install the
debug APK on your phone. Automated tests cover programmed-quantity restoration,
one-time entries, shopping-list independence, decimal validation, database
reopening, and transaction rollback.
