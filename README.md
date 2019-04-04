# ItemMerchant
Sell items to the bank.

## Motivation
The purpose of this plugin is to allow players to turn their acquired items into money. At the same time, admins need to be able to set prices and observe market behavior to inform thus decisions.

## Functionality
A player would initiate a sale via the `/sell` command. A chest menu opens, giving them an overview of which items in their inventory can be turned into money. For convenience, each item in the shop corresponds with the first location in the player's inventory. Through various clicks, single items, whole stacks, or entire inventories can be sold at once and turned into money immediately. The items will be removed from their inventory and the money granted immediately.

## Player Commands
- `/sell` Opoen the sell chest menu.

## Admin Commands
- `/im set <item> <price>` Update price.
- `/im get <item>` Look up price of one item.
- `/im list <pattern>` Look up range of item prices.
- `/im rank <what> <filters>...` Rank past sales.
  - *what*: `items|players`
  - *filters*:
    - `days <number>`
    - `item <name>`
    - `player <name>`

## Dependencies
This plugin requires the **Cavetale** standard libraries:
- `GenericEvents`
- `SQL`