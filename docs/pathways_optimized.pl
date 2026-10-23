:- style_check(-discontiguous).
:- dynamic event/3.
:- dynamic protein/1.
:- dynamic ligand/1.

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Archivo de Reglas de Rutas Biológicas Optimizado
% bioPatternsg - Motor de Inferencia
%
% Mejora respecto a pathwaysJPL.pl original:
% 1. Reemplazo de base(C) + buscar_en_lista por predicados directos event/3
% 2. Indexación Hash O(1) de primer argumento nativa en SWI-Prolog
% 3. Uso de member/2 estándar optimizado en C
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

%%%%%%%%%%%%%%%%%%%%%
% Búsqueda de Rutas sin lista restringida de objetos
%%%%%%%%%%%%%%%%%%%%%

% Evento inicio del patrón: ligando -> receptor o receptor -> receptor.
inicio(A, E, B) :-
    event(A, E, B),
    relations_start(R_start),
    member(E, R_start),
    restrictions_start(A, B).

% Evento final patrón tipo 1, tipo 2: transcription_factor -> objeto_de_cierre.
final(A, E, B) :-
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end),
    restrictions_end(A, B).

% Eventos intermedios en patrón tipo 1 más tipo 2: proteína -> proteína.
intermedios(A, E, B) :-
    event(A, E, B),
    relations_middle(R_middle),
    member(E, R_middle),
    restrictions_middle(A, B).

% Evento especial de cierre (patrón tipo 3, tipo 4).
eventoEspecial(A, E, B) :-
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end).

% Evento adicional para crear patrones tipo 5.
finalEspecial(A, E, B) :-
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end).


%%%%%%%%%%%%%%%%%%%%%
% Búsqueda de Rutas con lista restringida L de objetos
%%%%%%%%%%%%%%%%%%%%%

inicio_rest(A, E, B, L) :-
    member(A, L),
    member(B, L),
    event(A, E, B),
    relations_start(R_start),
    member(E, R_start),
    restrictions_start(A, B).

final_rest(A, E, B, L) :-
    member(A, L),
    member(B, L),
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end),
    restrictions_end(A, B).

intermedios_rest(A, E, B, L) :-
    member(A, L),
    member(B, L),
    event(A, E, B),
    relations_middle(R_middle),
    member(E, R_middle),
    restrictions_middle(A, B).

eventoEspecial_rest(A, E, B, L) :-
    member(A, L),
    member(B, L),
    event(A, E, B),
    relations_end(R_end),
    member(E, R_end).


%%%%%%%%%%%%%%%%%%%%%
% Restricciones para los objetos y tipos de interacciones
%%%%%%%%%%%%%%%%%%%%%

restrictions_start(A, B) :-
    (ligand(A) ; protein(A)),
    protein(B),
    A \= B.

relations_start([association, bind, interact, interaction]).

restrictions_end(A, B) :-
    protein(A),
    protein(B),
    A \= B.

relations_end([association, negative_correlation, inhibit, inhibition, positive_correlation, bind, cause, stimulate, stimulation, prevent, prevention]).

restrictions_middle(A, B) :-
    (ligand(B) ; protein(B)),
    A \= B.

relations_middle([association, interact, interaction]).
