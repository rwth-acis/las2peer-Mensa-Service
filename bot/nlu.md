## regex: number

- \d{1}

## regex: command

- \\\S{3,}

## synonym: mensa

- canteen

- mensa

- cantine

- restaurant

## lookup: city

- Aachen

- Bonn

- Frankfurt

- Leipzig

- Esch-sur-Alzette

- Luxembourg

- Wien

- Münster

- Chemnitz

## lookup: mensa

- Aachen, Mensa Academica

- Aachen, Mensa Vita

- Mensa Vita

- Mensa Academica

- mensa asee

## intent: help

- I need help

- What can you do

- hilfe

- help

- [/help](command)

- Ich brauch hilfe

- Hilfe

- was kannst du

- was kannst du alles

- /hilfe

## intent: quit

- quit

- stop

- [/stop](command)

- [/quit](command)

## intent: greeting

- hi

- hello

- hallo

- hey

- hallloo

- servus

- grues gott

- was geht alter

- [/start](command)

- was geht

- halli hallo

- jallo

- helo

- halo

- heyy

## intent: menu

- Whats on the menu?

- I want food

- [Aachen, Mensa Academica](mensa)

- whats on the menu for [Mensa Vita](mensa)

- whats on the menu for [mensa asee](mensa)

- was gibt es in der [mensa asee](mensa) ?

- menu für [Aachen, Mensa Academica](mensa)

- was gibt es heute in [Mensa Vita](mensa) ?

- was ist heute alles in der [Mensa Vita](mensa) ?

- was kann man heute in der [Mensa Vita](mensa) essen?

- I am hungry

- whats the menu?

- list the menu for canteens in [Aachen](city)

- list the menu for canteens

- [/menu](command)

- [/menu](command) [Mensa Academica](mensa)

## intent: listMensas

- What are mensas in [Chemnitz](city)

- What canteens are there in [Frankfurt](city)?

- What are canteens in [Leipzig](city)?

- What are canteens in [Münster](city)?

- welche mensen sind in [Chemnitz](city) geöffnet?

- List mensas in [Bonn](city)

- List mensas

- List canteens

- what are canteens in [berlin](city)

- canteens in [Münster](city)

## intent: startReview

- I want to review

- I want to start a review

- write review

- review

- I want to review my food

- I want to rate my food

- The food was nice

## intent: chooseMensaAndMeal

- I went to [Aachen, Mensa Academica](mensa) and had the [Vegetarisch](category)

- I went to [Aachen, Mensa Vita](mensa) and had the [Klassiker](category)

- ich war in die [Aachen, Mensa Academica](mensa) und hatte den [Klassiker](category)

- [Aachen, Mensa Vita](mensa), [Klassiker](category)

- [Aachen, Mensa Vita](mensa) and [Klassiker](category)

- [Klassiker](category)

- [Klassiker](category), [Aachen, Mensa Vita](mensa)

- [Vegetariar](category)

## intent: confirmation

- Yes

- Yep

- Yeah

- Yas

- Ye

- Yea

- Jo

- Ja

- Jep

- Thats correct

- correct

- Ok

- passt

- geht klar

- sure

- of course

## intent: rejection

- Nope

- No

- Nah

- Hell Nah

- Absolutely not

- This is not correct

- not correct

- no

- nananana

- no way

- nö

- kommt nicht in frage

- nein

- nee

## intent: stars

- I give my food [3](number) stars

- [4](number)

- [1](number) star

- [5](number) stars

## intent: number_selection

- [3](number)

- [4](number)

- [1](number)

## intent: thanks

- thanks

- thank you

- danke

- super

- mega thanks

- toll
