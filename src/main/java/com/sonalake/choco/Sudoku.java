package com.sonalake.choco;

import de.vandermeer.asciitable.AsciiTable;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.impl.FixedIntVarImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.String.format;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Given a graph, how can we colour each node so that no other nodes connect to
 * each other, and what is the minimum
 * number of nodes
 */
public class Sudoku {

  private static final int SIZE = 9;
  private static final int SQUARE_SIZE = 3;
  private static final int MIN_VALUE = 1;
  private static final int MAX_VALUE = SIZE;

  static public void main(String... args) throws IOException {

    // read stringGrind from file
    String filePath = "sudoku_grids_9.txt";

    // Lecture de la grille Sudoku à partir du fichier
    int[] stringGrind = readSudokuFromFile(filePath, SIZE);

    int[][] sudokuGrid = generateSudoku(stringGrind, SIZE);

    System.out.println("Size of the grid: " + sudokuGrid.length + "x" + sudokuGrid[0].length);

    // check that it's a square matrix
    for (int i = 0; i < sudokuGrid.length; i++) {
      if (sudokuGrid[i].length != sudokuGrid.length) {
        throw new IllegalArgumentException("The input matrix is not a square matrix " + i);
      }
    }
    // build the variables and constraint models
    Model model = new Model("sudoku");
    IntVar[][] grid = buildGrid(model, sudokuGrid);
    applyConnectionConstraints(model, grid);

    // print out the problem
    printGrid(grid, false, false);

    // solve it
    Solver solver = model.getSolver();
    solver.showShortStatistics();
    solver.setSearch(Search.minDomLBSearch(flatten(grid)));
    solver.solve();
    System.out.print("[");
    for (IntVar[] v : grid) {
      for (IntVar i : v) {
        System.out.print(i.getValue() + ",");
      }
      System.out.println();
    }
    System.out.print("]");

    // print out the solution
    printGrid(grid, true, true);
  }

  public static int[] readSudokuFromFile(String filePath, int size) {
    int[] sudokuGrid = new int[size * size]; // Grille Sudoku de 9x9

    try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
      String line = br.readLine();
      if (line != null) {
        String[] values = line.split(",");
        for (int i = 0; i < values.length && i < 81; i++) {
          if (!values[i].equals(" ")) {
            System.out.println("val=" + values[i].trim());

            sudokuGrid[i] = Integer.parseInt(values[i].trim());

          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return sudokuGrid;
  }

  public static IntVar[] flatten(IntVar[][] board) {
    IntVar[] flat = new IntVar[board.length * board[0].length];
    for (int i = 0; i < board.length; i++) {
      for (int j = 0; j < board[0].length; j++) {
        flat[i * board[0].length + j] = board[i][j];
      }
    }
    return flat;
  }

  /**
   * Build a grid in the form of [row][column]. Where we have a fixed value we
   * just use a simple intvar.
   * Where we have a 0 (i.e. an unknown) we put it a bounded intvar (from 1->9)
   *
   * @param model          the model into which the variables will be created
   * @param predefinedRows the predefined values
   * @return the created grid of variables.
   */
  private static IntVar[][] buildGrid(Model model, int[][] predefinedRows) {
    // this grid will contain variables in the same shape as the input
    IntVar[][] grid = new IntVar[SIZE][SIZE];

    // check all the predefined values
    // if they're 0: create them as bounded variables across the colour range (1-9)
    // otherwise create them as a constance
    for (int row = 0; row != SIZE; row++) {
      for (int col = 0; col != SIZE; col++) {
        // print predefinedRows[row]
        int value = predefinedRows[row][col];
        // is this an unknown? if so then create it as a bounded variable
        if (value < MIN_VALUE) {
          grid[row][col] = model.intVar(format("[%s.%s]", row, col), MIN_VALUE, MAX_VALUE);
        } else {
          // otherwise we have an actual value, so create it as a constant
          grid[row][col] = model.intVar(value);
        }
      }
    }

    return grid;
  }

  public static int[][] generateSudoku(int[] input, int size) {
    int[][] grid = new int[size][size];

    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        grid[i][j] = input[i * size + j];
      }
    }

    return grid;
  }

  /**
   * Given the grid, apply the constraints that stop cells in the same row /
   * column / square having the same values
   *
   * @param model the model in which constraints will be stored
   * @param grid  the grid
   */
  private static void applyConnectionConstraints(Model model, IntVar[][] grid) {
    // all the rows are different
    for (int i = 0; i != SIZE; i++) {
      model.allDifferent(getCellsInRow(grid, i)).post();
      model.allDifferent(getCellsInColumn(grid, i)).post();
      model.allDifferent(getCellsInSquare(grid, i)).post();
    }
  }

  /**
   * Get the variables that are in a given row
   *
   * @param grid the grid
   * @param row  the row
   * @return all the variables in this row
   */
  private static IntVar[] getCellsInRow(IntVar[][] grid, int row) {
    return grid[row];
  }

  /**
   * Get the variables that are in a given column
   *
   * @param grid   the grid
   * @param column the column
   * @return all the variables in this column
   */
  private static IntVar[] getCellsInColumn(IntVar[][] grid, int column) {
    return Stream.of(grid).map(row -> row[column]).toArray(IntVar[]::new);
  }

  /**
   * Get the variables in the given square within the overall grid. There being 9
   * 3x3 squares, starting at 0,0
   *
   * @param grid   the grid
   * @param square the square, numbered 1->9, going in rows
   * @return the variables in the given square
   */
  private static IntVar[] getCellsInSquare(IntVar[][] grid, int square) {
    List<IntVar> results = new ArrayList<>();
    // where does this square start in the grid
    int startRow = SQUARE_SIZE * (square / (SIZE / SQUARE_SIZE));
    int startColumn = SQUARE_SIZE * (square % (SIZE / SQUARE_SIZE));

    // get every cell in this square
    for (int row = startRow; row != startRow + SQUARE_SIZE; row++) {
      for (int column = startColumn; column != startColumn + SQUARE_SIZE; column++) {
        results.add(grid[row][column]);
      }
    }

    return results.toArray(new IntVar[0]);
  }

  /**
   * Print out the solution to standard out
   *
   * @param grid         the grid of variables
   * @param showSolution if set to true then any discovered values will be show,
   *                     if not, then only the
   *                     original problem will be show. If true then the original
   *                     values will be wrapped
   *                     in stars (*)
   * @throws IOException
   */
  private static void printGrid(IntVar[][] grid, boolean showSolution, boolean append) throws IOException {

    // We write the table out withthis
    AsciiTable at = new AsciiTable();
    at.addRule();

    // add each row to the table
    for (int row = 0; row != SIZE; row++) {
      List<String> labels = new ArrayList<>();
      for (int column = 0; column != SIZE; column++) {
        IntVar variable = grid[row][column];

        boolean isOriginalNumber = variable instanceof FixedIntVarImpl;

        // we show all numbers if we're showing the solution, but we always
        // show the original numbers
        boolean shouldShow = showSolution || isOriginalNumber;
        if (!shouldShow) {
          labels.add("");
        } else {
          // this is the number value for the cell, if we're showing the solution,
          // and this is an original value, we want to wrap it in stars
          String value = String.valueOf(variable.getValue());
          if (showSolution && isOriginalNumber) {
            value = "*" + value + "*";
          }
          labels.add(value);
        }
      }
      at.addRow(labels);
      at.addRule();
    }
    // put the at.reader in txt
    FileWriter writer = new FileWriter("result.txt", append);
    writer.write(at.render(20000));
    writer.write("\n\n\n------------------------------------------------------------------------\n\n\n");
    writer.close();
  }

}
