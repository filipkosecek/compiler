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
	: ID						#baseExpr
	| INT						#baseExpr
	| CHAR						#baseExpr
	| STRING					#baseExpr
	| ID ASSIGN expression				#assignExpr
	| INC ID (LEFT_SQUARE expression RIGHT_SQUARE)*	#incExpr
	| DEC ID (LEFT_SQUARE expression RIGHT_SQUARE)*	#decExpr
	| LEFT_BRACKET expression RIGHT_BRACKET		#subExpr
	| ID LEFT_BRACKET funcParamList* RIGHT_BRACKET	#funcCallExpr
	| ID LEFT_SQUARE expression RIGHT_SQUARE	#arrayExpr
	| MINUS expression				#unOpExpr
	| BIT_NOT expression				#unOpExpr
	| LOGICAL_NOT expression			#unOpExpr
	| LEFT_BRACKET TYPE RIGHT_BRACKET ID		#typeCastExpr
	| expression MULT expression			#binOpExpr
	| expression DIV expression			#binOpExpr
	| expression MOD expression			#binOpExpr
	| expression PLUS expression			#binOpExpr
	| expression MINUS expression			#binOpExpr
	| expression SHIFT_LEFT expression		#binOpExpr
	| expression SHIFT_RIGHT expression		#binOpExpr
	| expression LT expression			#binOpExpr
	| expression LTE expression			#binOpExpr
	| expression GT expression			#binOpExpr
	| expression GTE expression			#binOpExpr
	| expression EQ expression			#binOpExpr
	| expression NEQ expression			#binOpExpr
	| expression AMPERSAND expression		#binOpExpr
	| expression LOGICAL_OR expression		#binOpExpr
	| expression LOGICAL_AND expression		#binOpExpr
	| NEW TYPE LEFT_SQUARE expression RIGHT_SQUARE	#allocExpr
;

funcParamList
	: expression (COMMA expression)*
;

statement
	: for							#statementFor
	| if							#statementIf
	| switch						#statementSwitch
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

switch
	: SWITCH LEFT_BRACKET expression RIGHT_BRACKET LEFT_CURLY
	case+ defaultCase? RIGHT_CURLY
;

case
	: CASE expression COLON codeBlock
;

defaultCase
	: DEFAULT COLON codeBlock
;
