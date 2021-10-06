## regex: number

- \d{1}

## intent: help

- I need help
- What can you do
- hilfe
- help

## intent: greeting

- hi
- hello
- hallo

## synonym: mensa

- canteen
- mensa
- cantine
- restaurant

## intent: menu

- Whats on the menu?
- I want food
- [Aachen, Mensa Academica](mensa)
- whats on the menu for [Mensa Vita](mensa)
- I am hungry
- whats the menu?

## intent: listMensas

- What are mensas in [Aachen](city)?
- What canteens are there in [Frankfurt](city)?
- What are canteens in [Leipzig](city)?
- List mensas in [Bonn](city)

## lookup: city

- Aachen
- Bonn
- Frankfurt
- Leipzig
- Esch-sur-Alzette
- Luxembourg
- Wien

## lookup: mensa

- Aachen, Mensa Academica
- Aachen, Mensa Vita
- Mensa Vita
- Mensa Academica

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
- [Aachen, Mensa Vita](mensa), [Klassiker](category)
- [Aachen, Mensa Vita](mensa) and [Klassiker](category)
- [Klassiker](category)
- [Klassiker](category), [Aachen, Mensa Vita](mensa)

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

## intent: rejection

- Nope
- No
- Nah
- Hell Nah
- Absolutely not
- This is not correct
- not correct

## intent: averageStars

- I give my food [3](number) stars
- [4](number)
- [1](number) star
- [5](number) stars

## intent: thanks

- thanks
- thank you
- danke
