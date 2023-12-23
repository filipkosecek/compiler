lexer grammar cssLex;

AMPERSAND: '&' ;
TYPE: 'byte' | 'ubyte' | 'int' | 'uint' | 'void';
BREAK: 'break' ;
CONTINUE: 'continue' ;
CASE: 'case' ;
COLON: ':' ;
COMMA: ',' ;
DEC: '--' ;
DEFAULT: 'default' ;
NEW: 'new' ;
DELETE: 'delete' ;
ELIF: 'else' ' '+ 'if' ;
ELSE: 'else' ;
FOR: 'for' ;
IF: 'if' ;
RETURN: 'return' ;
SWITCH: 'switch' ;
PLUS: '+' ;
MINUS: '-' ;
MULT: '*' ;
DIV: '/' ;
MOD: '%' ;
ASSIGN: '=' ;
EQ: '==' ;
NEQ: '!=' ;
LT: '<' ;
LTE: '<=' ;
GT: '>' ;
GTE: '>=' ;
INC: '++' ;
LOGICAL_AND: '&&' ;
LOGICAL_OR: '||' ;
LOGICAL_NOT: '!' ;
BIT_NOT: '~' ;
SHIFT_LEFT: '<<' ;
SHIFT_RIGHT: '>>' ;
SEMICOLON: ';' ;
LEFT_BRACKET: '(' ;
RIGHT_BRACKET: ')' ;
LEFT_CURLY: '{' ;
RIGHT_CURLY: '}' ;
LEFT_SQUARE: '[' ;
RIGHT_SQUARE: ']' ;
INPUT: '==>' ;
OUTPUT: '<==' ;

fragment ONELINE_COMMENT
: '//' ~[\r\n]* ENDLINE
;

fragment COMMENT
: '/*' ('*' ~'/' | ~'*' .)* '*/'
;

ID
: '_'* [a-zA-Z] [a-zA-Z0-9_]*
;

STRING
: '"' ~["\r\n]* '"'
;

INT
: [0-9]+ | [1-9] [0-9]*
;

CHAR
: '\'' [\u0020-\u007e] '\''
;

fragment ENDLINE
: '\r'? '\n'
;

WS
: (ONELINE_COMMENT | COMMENT | ENDLINE | [ \t]) -> skip
;
