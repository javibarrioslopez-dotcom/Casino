import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Casino {

    static final Random RND = new Random();
    static final int APUESTAS = 0;
    static final int RESULTADO = 1;

    static class Banca {
        private int saldo = 50000;

        synchronized void recibir(int cantidad) {
            saldo += cantidad;
        }

        synchronized boolean pagar(int cantidad) {
            if (cantidad > saldo) {
                return false;
            }
            saldo -= cantidad;
            return true;
        }

        synchronized int getSaldo() {
            return saldo;
        }
    }

    static class Ruleta extends Thread {
        private final Banca banca;
        private int jugadores;
        private int apuestas = 0;
        private int comprobados = 0;
        private int fase = APUESTAS;
        private int numero = -1;
        private boolean activa = true;

        Ruleta(Banca banca, int jugadores) {
            this.banca = banca;
            this.jugadores = jugadores;
        }

        synchronized boolean apostar(Jugador j) throws InterruptedException {
            while (activa && fase != APUESTAS) {
                wait();
            }
            if (!activa) {
                return false;
            }
            int cantidad = j.cantidad();
            if (j.saldo < cantidad) {
                System.out.println(j.nombre + " se retira por falta de saldo");
                jugadores--;
                if (jugadores == 0) {
                    activa = false;
                }
                notifyAll();
                return false;
            }
            j.saldo -= cantidad;
            banca.recibir(cantidad);
            System.out.println(j.nombre + " apuesta " + cantidad + " € " + j.descripcion());
            apuestas++;
            if (apuestas == jugadores) {
                notifyAll();
            }
            while (activa && fase != RESULTADO) {
                wait();
            }
            return activa;
        }

        synchronized int getNumero() {
            return numero;
        }

        synchronized void esperarNuevoTurno() throws InterruptedException {
            comprobados++;
            if (comprobados == jugadores) {
                notifyAll();
            }
            while (activa && fase != APUESTAS) {
                wait();
            }
        }

        synchronized void finalizar() {
            if (activa) {
                activa = false;
                System.out.println("La banca no puede pagar. Fin de la simulación. Saldo banca: " + banca.getSaldo() + " €");
                notifyAll();
            }
        }

        synchronized boolean estaActiva() {
            return activa;
        }

        private synchronized void tirar() throws InterruptedException {
            while (activa && apuestas < jugadores) {
                wait();
            }
            if (!activa) {
                return;
            }
            numero = RND.nextInt(37);
            System.out.println("Resultado de la tirada: " + numero);
            fase = RESULTADO;
            notifyAll();
            while (activa && comprobados < jugadores) {
                wait();
            }
            if (!activa) {
                return;
            }
            System.out.println("Saldo banca: " + banca.getSaldo() + " €");
            apuestas = 0;
            comprobados = 0;
            fase = APUESTAS;
            notifyAll();
        }

        @Override
        public void run() {
            try {
                while (estaActiva()) {
                    Thread.sleep(3000);
                    tirar();
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    static abstract class Jugador extends Thread {
        final String nombre;
        final Ruleta ruleta;
        final Banca banca;
        int saldo = 1000;

        Jugador(int id, Ruleta ruleta, Banca banca) {
            this.nombre = getClass().getSimpleName() + " " + id;
            this.ruleta = ruleta;
            this.banca = banca;
        }

        int cantidad() {
            return 10;
        }

        abstract String descripcion();

        abstract boolean gana(int numero);

        abstract int premio();

        void actualizar(boolean gano) {
        }

        @Override
        public void run() {
            try {
                while (ruleta.apostar(this)) {
                    boolean gano = gana(ruleta.getNumero());
                    if (gano) {
                        if (!banca.pagar(premio())) {
                            System.out.println(nombre + " gana pero la banca no puede pagar");
                            ruleta.finalizar();
                            break;
                        }
                        saldo += premio();
                        System.out.println(nombre + " gana. Saldo: " + saldo + " €");
                    } else {
                        System.out.println(nombre + " pierde. Saldo: " + saldo + " €");
                    }
                    actualizar(gano);
                    ruleta.esperarNuevoTurno();
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    static class JugadorNumero extends Jugador {
        final int numero = 1 + RND.nextInt(36);

        JugadorNumero(int id, Ruleta ruleta, Banca banca) {
            super(id, ruleta, banca);
        }

        @Override
        String descripcion() {
            return "al número " + numero;
        }

        @Override
        boolean gana(int n) {
            return n == numero;
        }

        @Override
        int premio() {
            return 360;
        }
    }

    static class JugadorParImpar extends Jugador {
        final boolean par = RND.nextBoolean();

        JugadorParImpar(int id, Ruleta ruleta, Banca banca) {
            super(id, ruleta, banca);
        }

        @Override
        String descripcion() {
            return "a " + (par ? "PAR" : "IMPAR");
        }

        @Override
        boolean gana(int n) {
            return n != 0 && (n % 2 == 0) == par;
        }

        @Override
        int premio() {
            return 20;
        }
    }

    static class JugadorMartingala extends JugadorNumero {
        private int apuesta = 10;

        JugadorMartingala(int id, Ruleta ruleta, Banca banca) {
            super(id, ruleta, banca);
        }

        @Override
        int cantidad() {
            return apuesta;
        }

        @Override
        void actualizar(boolean gano) {
            apuesta = gano ? 10 : apuesta * 2;
        }
    }

    static List<Jugador> crearJugadores(Ruleta ruleta, Banca banca) {
        List<Jugador> jugadores = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            jugadores.add(new JugadorNumero(i, ruleta, banca));
        }
        for (int i = 5; i <= 8; i++) {
            jugadores.add(new JugadorParImpar(i, ruleta, banca));
        }
        for (int i = 9; i <= 12; i++) {
            jugadores.add(new JugadorMartingala(i, ruleta, banca));
        }
        return jugadores;
    }

    public static void main(String[] args) throws InterruptedException {
        Banca banca = new Banca();
        Ruleta ruleta = new Ruleta(banca, 12);
        List<Jugador> jugadores = crearJugadores(ruleta, banca);
        ruleta.start();
        for (Jugador j : jugadores) {
            j.start();
        }
        ruleta.join();
        for (Jugador j : jugadores) {
            j.join();
        }
    }
}