## ABOUT

Edit is a scripting language in progress. Use EditDemo.java to test.

## INPUT

This is currently the extent of it's functionality:

```

#This will be ignored

print "this tests the evaluator"

function ending(x) do
	x = plusOne(x)
	while x > 0 do
		print "This will print at the end x + 1 times"
		x = x - 1
	end
end

function plusOne(x) do
	while true do
		
		return x + 1

		print "This is unreachable code"
	end
	if not false then
		print "This is also unreachable"
	end
end

print #This will be ignored

x = 6

if x < 4 then
	print "This won't print"
else if x == 5 then
	print "This won't print either" 
else
	print "This will print" #This will be ignored
end

print true and false
print true or false
print true xand false
print true xor false
print not true

print

#This will be ignored

print "This is a" , "comma."
print "This is a" ; "semi-colon."

print

input "Pick a val for x: ", x

print

print 2 + x * -2
print 2 - x / 2
print x ^ 3 % 7
print "hello" + " " + "world"

print

print 2 > x or false
print 2 < x or false
print 2 <= x and true
print 2 >= x and true
print 2 == x and true

print

if x > 0 then
	for i = 0, i < x, i + 1 do
		print i
	end
else
	while x < 0 do
		print x
		x = x + 1
	end
end

print

ending(x)

#This will be ignored
```

## OUTPUT

```
This will print
false
true
false
true
false

This is a comma.
This is a       semi-colon.

Pick a val for x: 5

-8.0
-0.5
6.0
hello world

false
true
true
false
false

0.0
1.0
2.0
3.0
4.0

This will print at the end x + 1 times
This will print at the end x + 1 times
This will print at the end x + 1 times
This will print at the end x + 1 times
This will print at the end x + 1 times
```
