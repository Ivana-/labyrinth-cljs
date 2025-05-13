# labyrinth

Step-by-step puzzler game

## [Play with online demo!](https://codepen.io/Ivana4977517cb5/full/raaPVJG)

## Overview

This is an updated version of the game "Labyrinth", [described here](https://ivanov-andrey.itch.io/labyrinth), and written in its own dialect of the Lisp called Liscript.

Open link above for detailed description of rules, mission etc.

![Image](https://github.com/user-attachments/assets/f84ecc1d-b39d-4b1a-8643-4909ec746241 "Screenshot")

Changes from previous version:

- moves animation when map is shown, to clarify the game behaviour for not experienced users.

- new object type: mirror, reflects the move directions when one steps from it. Represented as "free cell" in responses!

- new object type: inner wall, so now you can't rely on "wall" response as map border :)

- new feature "Check steps": when this button pressed, game resets steps counter to zero and watches if user overflows the optimal amount of steps for reaching any point. If yes, the steps count label becames red.

So now you have at least 3 ways/missions to play:

- show the map, select (positioning on) any start point, select any destination point, and try to reach it in minimal steps amount.

- do not show the map and try to reconstruct it, as it was before.

- once you have reconstructed map and do know your position on it, try first mission, but without showing the map.

Good luck with even small maps with some mirrors & inner walls and have fun! :)

## Setup

To get an interactive development environment run:

    npx shadow-cljs watch client

And open your browser in `resources/public/index.html`.

To create a production build run:

    npx shadow-cljs release client

## License

Copyright © 2019

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.