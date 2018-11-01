import { Injectable } from '@angular/core';
import { Http, Response } from '@angular/http';
import {map} from 'rxjs/operators';


@Injectable()
export class EventScannerService {
    constructor (
        private http: Http
    ) {}

    getUsers() {
        return this.http.get(`http://sunnywiki.com/eventScanner/api/getData.php?users=true`)
            .pipe(map(res => res.json()));
    }

    getProjects() {
        return this.http.get(`http://sunnywiki.com/eventScanner/api/getData.php?projects=true`)
            .pipe(map(res => res.json()));
    }

    getEvents() {
        return this.http.get(`http://sunnywiki.com/eventScanner/api/getData.php?events=true`)
            .pipe(map(res => res.json()));
    }

    clearEvents() {
        return this.http.get(`http://sunnywiki.com/eventScanner/api/getData.php?clearEvents=true`)
            .pipe(map(res => res.json()));
    }
}
