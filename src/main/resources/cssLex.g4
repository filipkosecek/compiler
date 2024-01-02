lexer grammar cssLex;

IMPORT_STRING_LIB: 'import string' ;
TYPE_BYTE: 'byte' ;
TYPE_INT: 'int' ;
TYPE_VOID: 'void' ;
BREAK: 'break' ;
CONTINUE: 'continue' ;
COLON: ':' ;
COMMA: ',' ;
DEFAULT: 'default' ;
ELIF: 'else' ' '+ 'if' ;
ELSE: 'else' ;
WHILE: 'while' ;
IF: 'if' ;
RETURN: 'return' ;
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
LOGICAL_AND: '&&' ;
LOGICAL_OR: '||' ;
LOGICAL_NOT: '!' ;
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
: '/*' .*? '*/'
;

ID
: '_'* [a-zA-Z] [a-zA-Z0-9_]*
;

STRING
: '"' ~["\r\n]* '"'
;

INT
: '-'? [0-9]+ | [1-9] [0-9]*
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
