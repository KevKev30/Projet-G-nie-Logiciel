public class Grid{
    private int width;
    private int height;
    private Cell[][] cells;

    public Grid(int width, int height){
        this.width = width;
        this.height =height;
        this.cells = new Cell[width][height];
        initCells();
        buildNeighborhood();
    }

    private void initCells(){
        for (int x=0; x<width; x++){
            for (int y=0; y<height; y++){
                cells[x][y] = new Cell(0,State.SAFE, 1.0, 1, 1.0, x, y);
            }
        }
    }

    private void buildNeighborhood(){
        for (int x=0; x<width;x++){
            for (int y=0;y <height;y++){
                for (int dx=-1; dx<=1; dx++){
                    for (int dy=-1; dy<=1; dy++){
                        if (dx==0 && dy==0) continue;
                        int nx = x+dx;
                        int ny = y+dy;
                        if (nx>=0 && nx<width && ny>=0 && ny<height){
                            cells[x][y].addNeighbors(cells[nx][ny]);
                        }
                    }
                }
            }
        }
    }

    public void infectCell(int x, int y){
        if(isValid(x, y)){
            cells[x][y].setState(State.INFECTED);
        }
    }

    public Cell getCell (int x, int y){
        if (isValid(x, y)) return cells[x][y];
        return null;
    }

    public boolean isValid(int x, int y){
        return x>=0 && y>=0 && x<width && y<height;
    }

    public int getWidth(){
        return width;
    }
    public int getHeight(){
        return height;
    }

    public Cell[][] getCells(){
        return cells;
    }
}