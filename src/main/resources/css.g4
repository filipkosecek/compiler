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

variable
    : ID (LEFT_SQUARE expression RIGHT_SQUARE)*
;

expression
    : base=(INT | CHAR | STRING)                                    #baseExpr
    | variable                                                      #idExpr
	| ID (LEFT_SQUARE expression RIGHT_SQUARE)* ASSIGN expression   #assignExpr
	| INC ID (LEFT_SQUARE expression RIGHT_SQUARE)*                 #incExpr
	| DEC ID (LEFT_SQUARE expression RIGHT_SQUARE)*                 #decExpr
	| LEFT_BRACKET expression RIGHT_BRACKET                         #subExpr
	| ID LEFT_BRACKET funcParamList? RIGHT_BRACKET                  #funcCallExpr
	| unOp=(MINUS | LOGICAL_NOT) expression                         #unOpExpr
	| LEFT_BRACKET TYPE (LEFT_SQUARE RIGHT_SQUARE)*
	RIGHT_BRACKET variable                                          #typeCastExpr
	| expression binOp=(MULT | DIV | MOD) expression                #binOpExpr
	| expression binOp=(PLUS | MINUS) expression                    #binOpExpr
	| expression binOp=(LT | LTE | GT | GTE | EQ | NEQ) expression  #binOpExpr
	| expression binOp=LOGICAL_AND expression                       #binOpExpr
	| expression binOp=LOGICAL_OR expression                        #binOpExpr
	| NEW TYPE (LEFT_SQUARE expression RIGHT_SQUARE)*               #allocExpr
;

funcParamList
	: expression (COMMA expression)*
;

statement
	: while							#statementWhile
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

while
	: WHILE LEFT_BRACKET expression RIGHT_BRACKET LEFT_CURLY codeBlock RIGHT_CURLY
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
