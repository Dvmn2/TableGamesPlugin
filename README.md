# TableGamesPlugin

Tabletop card game plugin for Paper 1.21.11. Physical decks on tables, cards held by players, face-up and face-down
placement.

**Requirements:** Paper 1.21.11, Java 21, the plugin resource pack (card glyphs, item models, sounds under the
`table_games` namespace).

---

## Decks

| Type    | Cards        | Notes                                                     |
|---------|--------------|-----------------------------------------------------------|
| `poker` | 52 (2 – ace) | No trump                                                  |
| `durak` | 36 (6 – ace) | The bottom card of the deck is shown face-up as the trump |

## Commands and permissions

| Command                  | Description           | Permission        |
|--------------------------|-----------------------|-------------------|
| `/tablegames give poker` | Give a new poker deck | `tablegames.give` |
| `/tablegames give durak` | Give a new durak deck | `tablegames.give` |

The `tablegames.give` permission defaults to operators.

## Controls

Only the main hand is used. Placement is possible on the top face of a block only.

### Deck item in hand

| Action                              | Result                                      |
|-------------------------------------|---------------------------------------------|
| Right-click the top face of a block | Place the deck (main pile and discard pile) |
| Shift + right-click                 | Shuffle the deck                            |

### Main pile and discard pile

| Action                          | Result                                                      |
|---------------------------------|-------------------------------------------------------------|
| Right-click, empty hand         | Take the top card                                           |
| Shift + right-click, empty hand | Pick up the whole deck (only if all cards are in this pile) |
| Right-click with a card         | Insert the card at a random position in the pile            |
| Shift + right-click with a card | Put the card on top                                         |

### Card in hand

Effective within 3 blocks of the card's own deck.

| Action                      | Result                   |
|-----------------------------|--------------------------|
| Right-click a block         | Place the card face-up   |
| Shift + right-click a block | Place the card face-down |

The card is rotated to match the player's facing direction.

### Card on the table

| Action                                           | Result                              |
|--------------------------------------------------|-------------------------------------|
| Right-click, empty hand                          | Take the top card of the stack      |
| Shift + right-click, empty hand                  | Flip the whole stack                |
| Right-click with a card of the same deck         | Stack on top with a rotation offset |
| Shift + right-click with a card of the same deck | Stack on top with a position offset |

A card placed on top inherits the orientation of the stack.

## Notifications

Player messages are shown in the action bar. Messages about successful actions are not shown. Command usage and console
errors are sent to chat.

## Configuration

`plugins/TableGamesPlugin/config.yml`

```yaml
settings:
  # auto — by client locale (ru — Russian, otherwise English)
  # ru   — always Russian
  # en   — always English
  language: auto
```

## Data storage

Deck state is saved automatically every 5 seconds when changes are present, and on server shutdown.

## Build

```
./gradlew build
```

Artifact: `build/libs/`.

## License

Not specified.