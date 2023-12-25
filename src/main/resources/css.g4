grammar css;

import cssLex;

program
	: function+ EOF
;

varDeclBlock
	: TYPE declAssign (COMMA declAssign)* SEMICOLON
;

declTypeArray
	: LEFT_SQUARE expression? RIGHT_SQUARE
;

declAssign
	: ID declTypeArray* (ASSIGN expression)?
;

funcArg
	: TYPE ID (LEFT_SQUARE RIGHT_SQUARE)*	#funcArgClassic
	| TYPE AMPERSAND ID			#funcArgReference
;

function
	: TYPE ID LEFT_BRACKET argList? RIGHT_BRACKET LEFT_CURLY
	codeBlock RIGHT_CURLY
;

argList
	: funcArg (COMMA funcArg)*
;

codeBlock
	: codeFragment*
;

codeFragment
	: expression SEMICOLON	#codeFragmentExpr
	| statement		#codeFragmentStatement
	| varDeclBlock		#codeFragmentVarDecl
;

expression
    : base=(INT | CHAR | STRING)                                    #baseExpr
    | ID (LEFT_SQUARE expression RIGHT_SQUARE)*                     #idExpr
	| ID ASSIGN expression                                          #assignIdExpr
	| ID (LEFT_SQUARE expression RIGHT_SQUARE)+ ASSIGN expression   #assignArrayExpr
	| incDec=(INC | DEC) ID                                         #incDecIdExpr
	| incDec=(INC | DEC) ID (LEFT_SQUARE expression RIGHT_SQUARE)+  #incDecArrayExpr
	| LEFT_BRACKET expression RIGHT_BRACKET                         #subExpr
	| ID LEFT_BRACKET funcParamList* RIGHT_BRACKET                  #funcCallExpr
	| unOp=(MINUS | BIT_NOT | LOGICAL_NOT) expression               #unOpExpr
	| LEFT_BRACKET TYPE RIGHT_BRACKET ID                            #typeCastExpr
	| expression binOp=(MULT | DIV | MOD | PLUS | MINUS |
	SHIFT_LEFT | SHIFT_RIGHT | LT | LTE | GT | GTE | EQ | NEQ
	| AMPERSAND | LOGICAL_OR | LOGICAL_AND) expression              #binOpExpr
	| NEW TYPE (LEFT_SQUARE expression RIGHT_SQUARE)*               #allocExpr
;

funcParamList
	: expression (COMMA expression)*
;

statement
	: for							#statementFor
	| if							#statementIf
	| CONTINUE SEMICOLON					#statementCont
	| BREAK SEMICOLON					#statementBreak
	| RETURN expression SEMICOLON				#statementReturn
	| DELETE ID SEMICOLON					#statementDelete
	| INPUT ID (LEFT_SQUARE expression RIGHT_SQUARE)*
		SEMICOLON					#statementIO
	| OUTPUT ID (LEFT_SQUARE expression RIGHT_SQUARE)*
		SEMICOLON					#statementIO
;

for
	: FOR LEFT_BRACKET expression? SEMICOLON expression?
	SEMICOLON expression? RIGHT_BRACKET LEFT_CURLY codeBlock RIGHT_CURLY
;

if
	: IF LEFT_BRACKET expression RIGHT_BRACKET LEFT_CURLY codeBlock
	RIGHT_CURLY elif* else?
;

elif
	: ELIF LEFT_BRACKET expression RIGHT_BRACKET LEFT_CURLY
	codeBlock RIGHT_CURLY
;

else
	: ELSE LEFT_CURLY codeBlock RIGHT_CURLY
;
